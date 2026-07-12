package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.tabriko.repository.WalletTransactionRepository;
import uz.tabriko.service.WalletService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/wallet/callback/click")
@Slf4j
@Tag(name = "Wallet")
public class ClickCallbackController {

    private static final int CLICK_ERROR_NONE = 0;
    private static final int CLICK_ERROR_SIGN_FAILED = -1;
    private static final int CLICK_ERROR_NOT_FOUND = -5;
    private static final int CLICK_ERROR_ALREADY_PAID = -4;
    private static final int CLICK_ERROR_INTERNAL = -8;

    private static final int ACTION_PREPARE = 0;
    private static final int ACTION_COMPLETE = 1;

    private final WalletService walletService;
    private final WalletTransactionRepository walletTxRepo;

    @Value("${CLICK_SERVICE_ID:}")
    private String serviceId;

    @Value("${CLICK_SECRET_KEY:}")
    private String secretKey;

    public ClickCallbackController(WalletService walletService, WalletTransactionRepository walletTxRepo) {
        this.walletService = walletService;
        this.walletTxRepo = walletTxRepo;
    }

    @PostMapping
    @Operation(summary = "Click payment callback (public, no auth)")
    public ResponseEntity<Map<String, Object>> handle(
            @RequestParam("click_trans_id") String clickTransId,
            @RequestParam("service_id") String serviceIdParam,
            @RequestParam("click_paydoc_id") String clickPaydocId,
            @RequestParam("merchant_trans_id") String merchantTransId,
            @RequestParam("amount") String amountStr,
            @RequestParam("action") int action,
            @RequestParam("error") int error,
            @RequestParam("error_note") String errorNote,
            @RequestParam("sign_time") String signTime,
            @RequestParam("sign_string") String signString
    ) {
        String computed = md5(clickTransId + serviceId + secretKey + merchantTransId + amountStr + action + signTime);
        if (!MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), signString.getBytes(StandardCharsets.UTF_8))) {
            log.warn("[CLICK] Invalid signature for click_trans_id={} merchant_trans_id={}", clickTransId, merchantTransId);
            return ResponseEntity.ok(errorResponse(clickTransId, merchantTransId, CLICK_ERROR_SIGN_FAILED, "SIGN CHECK FAILED!"));
        }

        if (action == ACTION_PREPARE) {
            try {
                Long walletTxId = Long.parseLong(merchantTransId);
                if (!walletTxRepo.existsById(walletTxId)) {
                    return ResponseEntity.ok(errorResponse(clickTransId, merchantTransId, CLICK_ERROR_NOT_FOUND, "Transaction not found"));
                }
                log.info("[CLICK] Prepare for click_trans_id={} merchant_trans_id={}", clickTransId, merchantTransId);
                return ResponseEntity.ok(successResponse(clickTransId, merchantTransId));
            } catch (NumberFormatException e) {
                return ResponseEntity.ok(errorResponse(clickTransId, merchantTransId, CLICK_ERROR_NOT_FOUND, "Transaction not found"));
            }
        }

        if (action == ACTION_COMPLETE) {
            // Payment failed on Click's side — acknowledge without crediting
            if (error != 0) {
                log.warn("[CLICK] Payment failed error={} note={} for click_trans_id={}", error, errorNote, clickTransId);
                return ResponseEntity.ok(successResponse(clickTransId, merchantTransId));
            }

            try {
                Long walletTxId = Long.parseLong(merchantTransId);
                BigDecimal amount = new BigDecimal(amountStr);
                walletService.creditTopUp(walletTxId, amount, clickTransId);
                log.info("[CLICK] Credited wallet for click_trans_id={} merchant_trans_id={} amount={}", clickTransId, merchantTransId, amountStr);
                return ResponseEntity.ok(successResponse(clickTransId, merchantTransId));
            } catch (NumberFormatException e) {
                return ResponseEntity.ok(errorResponse(clickTransId, merchantTransId, CLICK_ERROR_NOT_FOUND, "Transaction not found"));
            } catch (Exception e) {
                log.error("[CLICK] Error processing callback for click_trans_id={}: {}", clickTransId, e.getMessage());
                return ResponseEntity.ok(errorResponse(clickTransId, merchantTransId, CLICK_ERROR_INTERNAL, e.getMessage()));
            }
        }

        return ResponseEntity.ok(errorResponse(clickTransId, merchantTransId, CLICK_ERROR_INTERNAL, "Unknown action"));
    }

    private Map<String, Object> successResponse(String clickTransId, String merchantTransId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("click_trans_id", clickTransId);
        r.put("merchant_trans_id", merchantTransId);
        r.put("error", CLICK_ERROR_NONE);
        r.put("error_note", "Success");
        return r;
    }

    private Map<String, Object> errorResponse(String clickTransId, String merchantTransId, int errorCode, String note) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("click_trans_id", clickTransId);
        r.put("merchant_trans_id", merchantTransId);
        r.put("error", errorCode);
        r.put("error_note", note);
        return r;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
