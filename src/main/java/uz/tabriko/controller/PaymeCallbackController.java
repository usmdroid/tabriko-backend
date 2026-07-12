package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.tabriko.repository.WalletTransactionRepository;
import uz.tabriko.service.WalletService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/wallet/callback/payme")
@Slf4j
@Tag(name = "Wallet")
public class PaymeCallbackController {

    // JSON-RPC 2.0 error codes per Payme spec
    private static final int ERR_INSUFFICIENT_PRIVILEGE = -32504;
    private static final int ERR_ORDER_NOT_FOUND = -32501;
    private static final int ERR_CHECK_ACCOUNT_NOT_FOUND = -31050; // CheckPerformTransaction: account not found
    private static final int ERR_CANNOT_CANCEL = -32503;
    private static final int ERR_INTERNAL = -32500;

    // Payme transaction states
    private static final int STATE_PENDING = 1;
    private static final int STATE_COMPLETED = 2;
    private static final int STATE_CANCELLED = -1;

    private final WalletService walletService;
    private final WalletTransactionRepository walletTxRepo;

    @Value("${PAYME_KEY:}")
    private String paymeKey;

    public PaymeCallbackController(WalletService walletService, WalletTransactionRepository walletTxRepo) {
        this.walletService = walletService;
        this.walletTxRepo = walletTxRepo;
    }

    @PostMapping
    @Operation(summary = "Payme JSON-RPC 2.0 callback (public, no auth)")
    public ResponseEntity<Map<String, Object>> handle(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body
    ) {
        Object requestId = body.get("id");

        if (!verifyBasicAuth(authHeader)) {
            log.warn("[PAYME] Unauthorized request, id={}", requestId);
            return ResponseEntity.ok(errorResponse(requestId, ERR_INSUFFICIENT_PRIVILEGE, "Insufficient privilege"));
        }

        String method = (String) body.get("method");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");

        try {
            return switch (method) {
                case "CheckPerformTransaction" -> checkPerformTransaction(requestId, params);
                case "CreateTransaction" -> createTransaction(requestId, params);
                case "PerformTransaction" -> performTransaction(requestId, params);
                case "CancelTransaction" -> cancelTransaction(requestId, params);
                default -> {
                    log.warn("[PAYME] Unknown method: {}", method);
                    yield ResponseEntity.ok(errorResponse(requestId, ERR_INTERNAL, "Unknown method: " + method));
                }
            };
        } catch (Exception e) {
            log.error("[PAYME] Error handling method={}: {}", method, e.getMessage());
            return ResponseEntity.ok(errorResponse(requestId, ERR_INTERNAL, e.getMessage()));
        }
    }

    private ResponseEntity<Map<String, Object>> checkPerformTransaction(Object requestId, Map<String, Object> params) {
        Long walletTxId = extractAccountId(params);
        if (walletTxId == null || !walletTxRepo.existsById(walletTxId)) {
            return ResponseEntity.ok(errorResponse(requestId, ERR_CHECK_ACCOUNT_NOT_FOUND, "Transaction not found"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allow", true);
        return ResponseEntity.ok(successResponse(requestId, result));
    }

    private ResponseEntity<Map<String, Object>> createTransaction(Object requestId, Map<String, Object> params) {
        String paymeId = (String) params.get("id");
        Long walletTxId = extractAccountId(params);
        long now = System.currentTimeMillis();

        if (walletTxId == null) {
            return ResponseEntity.ok(errorResponse(requestId, ERR_ORDER_NOT_FOUND, "Transaction not found"));
        }

        try {
            walletService.bindPaymeTransaction(walletTxId, paymeId);
        } catch (Exception e) {
            return ResponseEntity.ok(errorResponse(requestId, ERR_ORDER_NOT_FOUND, e.getMessage()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("create_time", now);
        result.put("transaction", paymeId);
        result.put("state", STATE_PENDING);
        log.info("[PAYME] CreateTransaction paymeId={} walletTxId={}", paymeId, walletTxId);
        return ResponseEntity.ok(successResponse(requestId, result));
    }

    private ResponseEntity<Map<String, Object>> performTransaction(Object requestId, Map<String, Object> params) {
        String paymeId = (String) params.get("id");
        long now = System.currentTimeMillis();

        var txOpt = walletTxRepo.findByProviderRef(paymeId);
        if (txOpt.isEmpty()) {
            return ResponseEntity.ok(errorResponse(requestId, ERR_ORDER_NOT_FOUND, "Transaction not found for paymeId: " + paymeId));
        }

        var tx = txOpt.get();
        // amount in Payme callback is in tiyins; convert to sum (UZS)
        Object amountRaw = params.get("amount");
        BigDecimal amountTiyin = new BigDecimal(amountRaw.toString());
        BigDecimal amount = amountTiyin.movePointLeft(2);

        try {
            walletService.creditTopUp(tx.getId(), amount, paymeId);
        } catch (Exception e) {
            return ResponseEntity.ok(errorResponse(requestId, ERR_INTERNAL, e.getMessage()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transaction", paymeId);
        result.put("perform_time", now);
        result.put("state", STATE_COMPLETED);
        log.info("[PAYME] PerformTransaction paymeId={} walletTxId={}", paymeId, tx.getId());
        return ResponseEntity.ok(successResponse(requestId, result));
    }

    private ResponseEntity<Map<String, Object>> cancelTransaction(Object requestId, Map<String, Object> params) {
        String paymeId = (String) params.get("id");
        long now = System.currentTimeMillis();

        var txOpt = walletTxRepo.findByProviderRef(paymeId);
        if (txOpt.isEmpty()) {
            return ResponseEntity.ok(errorResponse(requestId, ERR_ORDER_NOT_FOUND, "Transaction not found for paymeId: " + paymeId));
        }

        var tx = txOpt.get();
        try {
            walletService.cancelTopUp(tx.getId());
        } catch (Exception e) {
            // completed transaction cannot be cancelled
            return ResponseEntity.ok(errorResponse(requestId, ERR_CANNOT_CANCEL, e.getMessage()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transaction", paymeId);
        result.put("cancel_time", now);
        result.put("state", STATE_CANCELLED);
        log.info("[PAYME] CancelTransaction paymeId={} walletTxId={}", paymeId, tx.getId());
        return ResponseEntity.ok(successResponse(requestId, result));
    }

    private boolean verifyBasicAuth(String authHeader) {
        if (paymeKey == null || paymeKey.isBlank()) return false;
        if (authHeader == null || !authHeader.startsWith("Basic ")) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)), StandardCharsets.UTF_8);
            if (!decoded.startsWith("Paycom:")) return false;
            String key = decoded.substring("Paycom:".length());
            // Constant-time compare
            byte[] expected = paymeKey.getBytes(StandardCharsets.UTF_8);
            byte[] actual = key.getBytes(StandardCharsets.UTF_8);
            return java.security.MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Long extractAccountId(Map<String, Object> params) {
        try {
            Map<String, Object> account = (Map<String, Object>) params.get("account");
            if (account == null) return null;
            return Long.parseLong(account.get("account_id").toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> successResponse(Object requestId, Map<String, Object> result) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", requestId);
        r.put("result", result);
        return r;
    }

    private Map<String, Object> errorResponse(Object requestId, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", requestId);
        r.put("error", error);
        return r;
    }
}
