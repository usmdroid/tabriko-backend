package uz.tabriko.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.tabriko.domain.entity.PlatformSettingsEntity;
import uz.tabriko.domain.entity.UserDevice;
import uz.tabriko.repository.PlatformSettingsRepository;
import uz.tabriko.repository.UserDeviceRepository;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DeviceIntegrityFilter extends OncePerRequestFilter {

    private final UserDeviceRepository deviceRepo;
    private final PlatformSettingsRepository settingsRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String deviceId = request.getHeader("X-Device-Id");
        if (deviceId == null || deviceId.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<UserDevice> deviceOpt = deviceRepo.findByUserIdAndDeviceId(principal.getUserId(), deviceId);
        if (deviceOpt.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        UserDevice device = deviceOpt.get();

        if (device.isBlocked()) {
            writeBlockedResponse(response);
            return;
        }

        PlatformSettingsEntity settings = settingsRepo.findById(1).orElseGet(PlatformSettingsEntity::new);
        if (settings.isBlockRootedDevices() && (device.isRooted() || Boolean.FALSE.equals(device.getGenuine()))) {
            writeBlockedResponse(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeBlockedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"DEVICE_BLOCKED\",\"message\":\"Device is blocked.\"}");
    }
}
