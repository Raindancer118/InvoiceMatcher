package de.pegasus.invoicematcher.api.dto;

import java.util.List;

/**
 * Kandidatenwerte fuer den "Verbindung testen"-Knopf - koennen noch ungespeichert sein. Eine
 * leere Schluesselliste heisst hier (anders als bei {@link KiRequest}) tatsaechlich "kein
 * Schluessel", nicht "unveraendert": es gibt nichts zu testen, wenn nichts eingegeben wurde.
 */
public record KiTestRequest(String fastModell, List<String> fastSchluessel,
                            String smartModell, List<String> smartSchluessel) {
}
