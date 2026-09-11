package de.pegasus.invoicematcher.api.dto;

/** Ergebnis des "Verbindung testen"-Knopfs, je Eskalationsstufe. */
public record KiTestResponse(boolean fastOk, String fastMeldung, boolean smartOk, String smartMeldung) {
}
