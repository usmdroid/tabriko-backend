package uz.tabriko.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.media.upload-dir:./uploads}")
    private String uploadDir;

    // Order-delivery media ("media/" folder) is intentionally NOT registered here: it must
    // only be reachable through the authenticated, ownership-checked MediaController stream
    // endpoint. Portfolio, KYC, and application files remain served as plain static files —
    // unchanged pre-existing behavior for those folders.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/portfolio/**")
                .addResourceLocations("file:" + uploadDir + "/portfolio/");
        registry.addResourceHandler("/files/kyc/**")
                .addResourceLocations("file:" + uploadDir + "/kyc/");
        registry.addResourceHandler("/files/applications/**")
                .addResourceLocations("file:" + uploadDir + "/applications/");
        // Public profile imagery (creator avatar/banner, user avatar) — served statically
        // when the LOCAL storage provider is active; S3 provider serves these from the bucket.
        registry.addResourceHandler("/files/avatars/**")
                .addResourceLocations("file:" + uploadDir + "/avatars/");
        registry.addResourceHandler("/files/banners/**")
                .addResourceLocations("file:" + uploadDir + "/banners/");
    }
}
