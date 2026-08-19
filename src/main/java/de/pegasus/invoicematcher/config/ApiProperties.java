package de.pegasus.invoicematcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Einstellungen der HTTP-Schnittstelle.
 *
 * @param corsAllowedOrigins Herkuenfte, die das Frontend nutzen darf
 */
@ConfigurationProperties("invoicematcher.api")
public record ApiProperties(List<String> corsAllowedOrigins) {

    public List<String> corsAllowedOrigins() {
        return corsAllowedOrigins == null ? List.of() : corsAllowedOrigins;
    }
}
