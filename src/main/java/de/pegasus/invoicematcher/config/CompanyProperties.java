package de.pegasus.invoicematcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Das eigene Unternehmen.
 *
 * <p>Wird der KI-Klassifikation als Kontext mitgegeben, damit sie erkennt, ob ein Beleg aus
 * eigener Sicht Eingang (wir sind Empfaenger) oder Ausgang (wir sind Aussteller) ist.
 */
@ConfigurationProperties("invoicematcher.company")
public record CompanyProperties(String name, String vatId, String taxNumber) {
}
