package de.pegasus.invoicematcher.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Oeffnet die API fuer das Frontend.
 *
 * <p>Das Frontend laeuft als eigenstaendige Astro-Anwendung auf einer anderen Herkunft und
 * spricht diese API als reinen JSON-Dienst an.
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final ApiProperties properties;

    public WebConfiguration(ApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (properties.corsAllowedOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(properties.corsAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
