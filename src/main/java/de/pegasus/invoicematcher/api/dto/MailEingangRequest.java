package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.config.MailIntakeProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * Belegannahme-per-Mail-Einstellungen aus der Oberflaeche.
 *
 * <p>{@code passwort} ist bewusst kein Pflichtfeld und ohne Validierung des Inhalts: leer
 * gelassen bedeutet "unveraendert", siehe {@link de.pegasus.invoicematcher.config.MailIntakeSettingsService#update}.
 * Ob Host/Benutzername gesetzt sein muessen, haengt von {@code aktiv} ab und wird dort geprueft,
 * nicht hier auf DTO-Ebene.
 */
public record MailEingangRequest(boolean aktiv, @NotBlank String protokoll, String host, @NotNull Integer port,
                                 String benutzername, String passwort, @NotBlank String ordner,
                                 @NotBlank String verarbeitetOrdner, @NotBlank String fehlgeschlagenOrdner,
                                 @NotNull Long taktSekunden, @NotNull Integer maxNachrichtenProDurchlauf,
                                 @NotNull Long maxAnhangGroesseKb, List<String> erlaubteAbsender) {

    public MailIntakeProperties toMailIntakeProperties() {
        return new MailIntakeProperties(aktiv, protokoll, host, port, benutzername, passwort, ordner,
                verarbeitetOrdner, fehlgeschlagenOrdner, Duration.ofSeconds(taktSekunden),
                maxNachrichtenProDurchlauf, DataSize.ofKilobytes(maxAnhangGroesseKb),
                erlaubteAbsender == null ? List.of() : erlaubteAbsender);
    }
}
