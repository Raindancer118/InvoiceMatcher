package de.pegasus.invoicematcher.api.dto;

import java.util.List;

/**
 * Auswahl der zu uebergebenden Pruefvorgaenge.
 *
 * @param vorgaenge leere oder fehlende Liste bedeutet: alle freigegebenen, noch offenen Belege
 */
public record ExportRequest(List<String> vorgaenge) {

    public boolean isEmpty() {
        return vorgaenge == null || vorgaenge.isEmpty();
    }
}
