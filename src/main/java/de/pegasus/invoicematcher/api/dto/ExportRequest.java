package de.pegasus.invoicematcher.api.dto;

import java.util.List;

/**
 * Auswahl der zu uebergebenden Pruefvorgaenge.
 *
 * @param vorgaenge leere oder fehlende Liste bedeutet: alle freigegebenen, noch offenen Belege
 * @param erneut erlaubt bei expliziter Auswahl auch schon exportierte Belege
 */
public record ExportRequest(List<String> vorgaenge, Boolean erneut) {

    public ExportRequest {
        erneut = Boolean.TRUE.equals(erneut);
    }

    public boolean isEmpty() {
        return vorgaenge == null || vorgaenge.isEmpty();
    }
}
