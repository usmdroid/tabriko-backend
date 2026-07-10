package uz.tabriko.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.Delivery;
import uz.tabriko.domain.entity.Order;
import uz.tabriko.domain.enums.NotificationType;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.dto.response.MediaUploadResponse;
import uz.tabriko.dto.response.SignedUrlResponse;
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.repository.DeliveryRepository;
import uz.tabriko.repository.OrderRepository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final OrderRepository orderRepo;
    private final DeliveryRepository deliveryRepo;
    private final MediaStorageService mediaStorage;
    private final NotificationService notificationService;

    private static final Set<String> ALLOWED_EXTENSIONS =
        Set.of("mp4", "mov", "mp3", "m4a", "aac");

    private static final long SIGNED_URL_TTL_SECONDS = 3600L;

    @Value("${app.media.max-size-mb:100}")
    private long maxSizeMb;

    @Transactional
    public MediaUploadResponse uploadMedia(UUID creatorId, UUID orderId, MultipartFile file) {
        validateFile(file);

        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getCreator().getId().equals(creatorId)) {
            throw ApiException.forbidden("Not your order");
        }
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.IN_PROGRESS) {
            throw ApiException.badRequest("Order cannot be delivered in current status");
        }

        String cleanUrl = mediaStorage.store(file, "media");
        String watermarkedUrl = mediaStorage.applyWatermark(cleanUrl);

        Delivery delivery = deliveryRepo.findByOrderId(orderId).orElse(new Delivery());
        delivery.setOrder(order);
        delivery.setMediaUrlClean(cleanUrl);
        delivery.setMediaUrlWatermarked(watermarkedUrl);
        delivery.setWatermarked(true);
        delivery.setDeliveredAt(Instant.now());
        deliveryRepo.save(delivery);

        order.setStatus(OrderStatus.DELIVERED);
        orderRepo.save(order);

        notificationService.sendNotification(
            order.getClient().getId(),
            "Order delivered",
            "Your order from " + order.getCreator().getName() + " is ready",
            NotificationType.ORDER_DELIVERED
        );

        MediaUploadResponse r = new MediaUploadResponse();
        r.setOrderId(orderId);
        r.setWatermarkedUrl(watermarkedUrl);
        return r;
    }

    public SignedUrlResponse getDownloadUrl(UUID userId, UUID orderId) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> ApiException.notFound("Order not found"));

        boolean isClient = order.getClient().getId().equals(userId);
        boolean isCreator = order.getCreator().getId().equals(userId);
        if (!isClient && !isCreator) {
            throw ApiException.forbidden("Not your order");
        }
        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.ACCEPTED) {
            throw ApiException.forbidden("Order is not delivered yet");
        }

        Delivery delivery = deliveryRepo.findByOrderId(orderId)
            .orElseThrow(() -> ApiException.notFound("Delivery not found"));

        String mediaUrl = order.getStatus() == OrderStatus.ACCEPTED
            ? delivery.getMediaUrlClean()
            : delivery.getMediaUrlWatermarked();
        String signedUrl = mediaStorage.signedUrl(mediaUrl, userId, SIGNED_URL_TTL_SECONDS);
        return new SignedUrlResponse(signedUrl, SIGNED_URL_TTL_SECONDS);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is required");
        }
        long maxBytes = maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw ApiException.badRequest("File size exceeds limit of " + maxSizeMb + "MB");
        }
        String name = file.getOriginalFilename();
        String ext = (name != null && name.contains("."))
            ? name.substring(name.lastIndexOf('.') + 1).toLowerCase()
            : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw ApiException.badRequest("Unsupported file type. Allowed: mp4, mov, mp3, m4a, aac");
        }
    }
}
