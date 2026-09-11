package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.config.AiProperties;

/** KI-Modelleinstellungen fuer die Oberflaeche - nie die Schluessel selbst. */
public record KiResponse(String fastModell, boolean fastSchluesselGesetzt, int fastSchluesselAnzahl,
                         String smartModell, boolean smartSchluesselGesetzt, int smartSchluesselAnzahl) {

    public static KiResponse of(AiProperties ai) {
        return new KiResponse(
                ai.fast().model(), !ai.fast().apiKeys().isEmpty(), ai.fast().apiKeys().size(),
                ai.smart().model(), !ai.smart().apiKeys().isEmpty(), ai.smart().apiKeys().size());
    }
}
