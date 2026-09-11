package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.config.AiProperties;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * KI-Modelleinstellungen aus der Oberflaeche.
 *
 * <p>Leere Schluessellisten bedeuten "unveraendert", wie beim Mail-Passwort (siehe
 * {@link de.pegasus.invoicematcher.config.AiSettingsService#update}) - gespeicherte Schluessel
 * werden aus Sicherheitsgruenden nie im Klartext an die Oberflaeche zurueckgegeben.
 */
public record KiRequest(@NotBlank String fastModell, List<String> fastSchluessel,
                        @NotBlank String smartModell, List<String> smartSchluessel) {

    public AiProperties toAiProperties() {
        return new AiProperties(
                new AiProperties.Tier(fastModell, fastSchluessel == null ? List.of() : fastSchluessel),
                new AiProperties.Tier(smartModell, smartSchluessel == null ? List.of() : smartSchluessel));
    }
}
