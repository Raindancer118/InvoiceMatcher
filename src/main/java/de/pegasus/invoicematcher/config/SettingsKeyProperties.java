package de.pegasus.invoicematcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Schluessel fuer {@link SecretCipher}.
 *
 * <p>Ohne Vorgabewert in {@code application.yml}: fehlt {@code INVOICEMATCHER_SETTINGS_KEY},
 * scheitert der Start mit einer klaren Meldung statt mit einem Geheimnis, das sich nicht mehr
 * entschluesseln laesst, sobald ein Platzhalter-Schluessel greift.
 *
 * @param key 32 Byte, Base64-kodiert - {@code openssl rand -base64 32}
 */
@ConfigurationProperties("invoicematcher.settings")
public record SettingsKeyProperties(String key) {
}
