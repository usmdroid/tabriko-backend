package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ultra-light keep-alive endpoint. No DB, no auth, tiny plain-text body.
 * Hit by an external cron (e.g. cron-job.org) every ~10 min during active
 * hours so the free-tier Render instance does not spin down (~15 min idle →
 * ~50s cold start otherwise).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health")
public class PingController {

    @GetMapping("/ping")
    @Operation(summary = "Keep-alive ping (no DB, no auth)")
    public String ping() {
        return "ok";
    }
}
