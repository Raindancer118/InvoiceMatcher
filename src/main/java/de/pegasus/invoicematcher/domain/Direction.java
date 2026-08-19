package de.pegasus.invoicematcher.domain;

/** Belegrichtung aus Sicht des eigenen Unternehmens. */
public enum Direction {
    /** Der Beleg wurde von einem Lieferanten gestellt (Kreditor). */
    EINGANG,
    /** Der Beleg wurde an einen Kunden gestellt (Debitor). */
    AUSGANG
}
