package de.pegasus.invoicematcher.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Oeffnet die API fuer das Frontend.
 *
 * <p>Das Frontend laeuft als eigenstaendige Astro-Anwendung auf einer anderen Herkunft und
 * spricht diese API als reinen JSON-Dienst an.
 *
 * <p>Die Herkuenfte stehen als Bean und nicht als {@code addCorsMappings}: der Preflight kommt
 * ohne Zugriffstoken, und die Sicherheitskette wuerde ihn abweisen, bevor Spring MVC ihn
 * ueberhaupt sieht.
 */
@Configuration
public class WebConfiguration {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(ApiProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (properties.corsAllowedOrigins().isEmpty()) {
            return source;
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.corsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
