package uz.tabriko.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.domain.entity.Delivery;
import uz.tabriko.domain.entity.Order;
import uz.tabriko.domain.entity.User;
import uz.tabriko.domain.enums.OrderStatus;
import uz.tabriko.domain.enums.Role;
import uz.tabriko.dto.response.SignedUrlResponse;
import uz.tabriko.infrastructure.media.MediaStorageService;
import uz.tabriko.repository.DeliveryRepository;
import uz.tabriko.repository.OrderRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock OrderRepository orderRepo;
    @Mock DeliveryRepository deliveryRepo;
    @Mock MediaStorageService mediaStorage;
    @Mock NotificationService notificationService;

    @InjectMocks MediaService mediaService;

    private UUID clientId, creatorId, orderId, strangerId;
    private User client, creator;
    private Order order;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mediaService, "maxSizeMb", 100L);

        clientId = UUID.randomUUID();
        creatorId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        client = new User();
        client.setId(clientId);
        client.setName("Client");
        client.setRole(Role.CLIENT);

        creator = new User();
        creator.setId(creatorId);
        creator.setName("Creator");
        creator.setRole(Role.CREATOR);

        order = new Order();
        order.setId(orderId);
        order.setClient(client);
        order.setCreator(creator);
        order.setStatus(OrderStatus.PENDING);
    }

    // ===== Upload file type validation =====

    @Test
    void uploadMedia_allowedMp4File_success() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "video.mp4", "video/mp4", new byte[1024]);

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(mediaStorage.store(any(), eq("media"))).thenReturn("clean://url");
        when(mediaStorage.applyWatermark("clean://url")).thenReturn("watermarked://url");
        when(deliveryRepo.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(deliveryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = mediaService.uploadMedia(creatorId, orderId, file);

        assertThat(result.getWatermarkedUrl()).isEqualTo("watermarked://url");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void uploadMedia_rejectedBadExtension() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "document.pdf", "application/pdf", new byte[100]);

        assertThatThrownBy(() -> mediaService.uploadMedia(creatorId, orderId, file))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Unsupported file type");

        verify(orderRepo, never()).findById(any());
    }

    @Test
    void uploadMedia_rejectedFileTooLarge() {
        // 101 MB > 100 MB limit
        byte[] bigData = new byte[101 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
            "file", "video.mp4", "video/mp4", bigData);

        assertThatThrownBy(() -> mediaService.uploadMedia(creatorId, orderId, file))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("File size exceeds limit");
    }

    @Test
    void uploadMedia_rejectedEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "video.mp4", "video/mp4", new byte[0]);

        assertThatThrownBy(() -> mediaService.uploadMedia(creatorId, orderId, file))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("File is required");
    }

    // ===== Upload authorization (only creator) =====

    @Test
    void uploadMedia_forbiddenForNonCreator() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "video.mp4", "video/mp4", new byte[100]);

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> mediaService.uploadMedia(strangerId, orderId, file))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Not your order");
    }

    @Test
    void uploadMedia_forbiddenForClient() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "video.mp4", "video/mp4", new byte[100]);

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        // Even the client cannot upload — only the creator
        assertThatThrownBy(() -> mediaService.uploadMedia(clientId, orderId, file))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Not your order");
    }

    // ===== Download URL — IDOR protection =====

    @Test
    void getDownloadUrl_forbiddenForStranger() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> mediaService.getDownloadUrl(strangerId, orderId))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Not your order");
    }

    // ===== Download URL — correct URL by order status =====

    @Test
    void getDownloadUrl_deliveredStatus_returnsWatermarked() {
        order.setStatus(OrderStatus.DELIVERED);
        Delivery delivery = buildDelivery();

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(deliveryRepo.findByOrderId(orderId)).thenReturn(Optional.of(delivery));
        when(mediaStorage.signedUrl(eq("watermarked://url"), eq(clientId), anyLong()))
            .thenReturn("signed://watermarked");

        SignedUrlResponse resp = mediaService.getDownloadUrl(clientId, orderId);

        assertThat(resp.getUrl()).isEqualTo("signed://watermarked");
        verify(mediaStorage).signedUrl("watermarked://url", clientId, 3600L);
    }

    @Test
    void getDownloadUrl_acceptedStatus_returnsCleanUrl() {
        order.setStatus(OrderStatus.ACCEPTED);
        Delivery delivery = buildDelivery();

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(deliveryRepo.findByOrderId(orderId)).thenReturn(Optional.of(delivery));
        when(mediaStorage.signedUrl(eq("clean://url"), eq(clientId), anyLong()))
            .thenReturn("signed://clean");

        SignedUrlResponse resp = mediaService.getDownloadUrl(clientId, orderId);

        assertThat(resp.getUrl()).isEqualTo("signed://clean");
        verify(mediaStorage).signedUrl("clean://url", clientId, 3600L);
    }

    @Test
    void getDownloadUrl_pendingStatus_forbidden() {
        order.setStatus(OrderStatus.PENDING);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> mediaService.getDownloadUrl(clientId, orderId))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("not delivered");
    }

    @Test
    void getDownloadUrl_creatorCanAlsoDownloadOwnOrder() {
        order.setStatus(OrderStatus.DELIVERED);
        Delivery delivery = buildDelivery();

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(deliveryRepo.findByOrderId(orderId)).thenReturn(Optional.of(delivery));
        when(mediaStorage.signedUrl(eq("watermarked://url"), eq(creatorId), anyLong()))
            .thenReturn("signed://watermarked");

        SignedUrlResponse resp = mediaService.getDownloadUrl(creatorId, orderId);

        assertThat(resp.getUrl()).isEqualTo("signed://watermarked");
    }

    @Test
    void getDownloadUrl_signedUrlTtlIs3600() {
        order.setStatus(OrderStatus.DELIVERED);
        Delivery delivery = buildDelivery();

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(deliveryRepo.findByOrderId(orderId)).thenReturn(Optional.of(delivery));
        when(mediaStorage.signedUrl(any(), any(), anyLong())).thenReturn("signed://url");

        SignedUrlResponse resp = mediaService.getDownloadUrl(clientId, orderId);

        assertThat(resp.getExpiresInSeconds()).isEqualTo(3600L);
    }

    // helpers

    private Delivery buildDelivery() {
        Delivery d = new Delivery();
        d.setOrder(order);
        d.setMediaUrlClean("clean://url");
        d.setMediaUrlWatermarked("watermarked://url");
        d.setWatermarked(true);
        return d;
    }
}
