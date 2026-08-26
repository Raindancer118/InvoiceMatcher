package de.pegasus.invoicematcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Absicherung der API.
 *
 * <p>Die Oberflaeche meldet sich bei Authentik an und reicht das Zugriffstoken an die API
 * weiter; hier wird es geprueft. Ohne diese Pruefung waere die Anmeldung im Frontend wertlos -
 * die API liegt im selben Netz und waere ohne Token erreichbar.
 *
 * @param issuerUri      Herausgeber der Token, z.B. {@code https://portal.tstieh.de/application/o/invoicematcher/}.
 *                       Leer bedeutet: keine Pruefung, nur fuer lokale Entwicklung gedacht.
 * @param requiredGroup  Gruppe, die ein Konto haben muss; leer laesst jedes gueltige Token zu
 * @param groupsClaim    Name des Claims, in dem Authentik die Gruppen liefert
 */
@ConfigurationProperties("invoicematcher.security")
public record SecurityProperties(String issuerUri, String requiredGroup, String groupsClaim) {

    public SecurityProperties {
        groupsClaim = groupsClaim == null || groupsClaim.isBlank() ? "groups" : groupsClaim;
        requiredGroup = requiredGroup == null || requiredGroup.isBlank() ? null : requiredGroup.strip();
    }

    /** Ohne Herausgeber gibt es nichts zu pruefen. */
    public boolean isEnabled() {
        return issuerUri != null && !issuerUri.isBlank();
    }
}
