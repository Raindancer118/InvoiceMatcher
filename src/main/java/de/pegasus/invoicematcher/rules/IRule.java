package de.pegasus.invoicematcher.rules;

import org.springframework.core.Ordered;

/**
 * Ein Schritt der Ablauflogik.
 *
 * <p>Regeln arbeiten auf einem gemeinsamen {@link ClassificationContext} und werden in fester
 * Reihenfolge angewandt. Jede Regel prueft genau einen Sachverhalt, haelt ihre Begruendung als
 * Finding fest und veraendert den Belegtyp nur, wenn ihre Bedingung greift.
 */
public interface IRule extends Ordered {

    /** Die Nummer aus dem Regelwerk, z.B. "1.2". Erscheint in jedem Finding. */
    String id();

    /** Fachliche Beschreibung fuer Protokoll und Review-Oberflaeche. */
    String description();

    void apply(ClassificationContext context);
}
