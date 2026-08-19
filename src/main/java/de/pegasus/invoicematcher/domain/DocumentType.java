package de.pegasus.invoicematcher.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Das einzige Klassifikationsergebnis des Prototyps. Neben den DATEV-Standardbelegtypen
 * existieren die beiden individuellen Typen "... zur Pruefung": der Beleg ist erkennbar eine
 * Rechnung, weist aber einen Mangel auf und darf deshalb keine Buchungsdaten mitfuehren.
 */
public enum DocumentType {

    RECHNUNGSEINGANG("Rechnungseingang", Direction.EINGANG, false),
    RECHNUNGSAUSGANG("Rechnungsausgang", Direction.AUSGANG, false),
    RECHNUNGSEINGANG_ZUR_PRUEFUNG("Rechnungseingang zur Prüfung", Direction.EINGANG, true),
    RECHNUNGSAUSGANG_ZUR_PRUEFUNG("Rechnungsausgang zur Prüfung", Direction.AUSGANG, true),
    SONSTIGE("Sonstige", null, false);

    private final String label;
    private final Direction direction;
    private final boolean review;

    DocumentType(String label, Direction direction, boolean review) {
        this.label = label;
        this.direction = direction;
        this.review = review;
    }

    /** Der Wert, wie er im DATEV-Zwischenformat erscheint. */
    @JsonValue
    public String label() {
        return label;
    }

    public Direction direction() {
        return direction;
    }

    public boolean isReview() {
        return review;
    }

    /** Nur Belege ohne "zur Pruefung"-Zusatz duerfen einen vollstaendigen Buchungssatz tragen. */
    public boolean carriesBookingData() {
        return direction != null && !review;
    }

    public static DocumentType bookableFor(Direction direction) {
        return direction == Direction.AUSGANG ? RECHNUNGSAUSGANG : RECHNUNGSEINGANG;
    }

    /** Ueberfuehrt einen buchbaren Typ in seine Pruefvariante; "Sonstige" bleibt unveraendert. */
    public DocumentType toReview() {
        return switch (this) {
            case RECHNUNGSEINGANG, RECHNUNGSEINGANG_ZUR_PRUEFUNG -> RECHNUNGSEINGANG_ZUR_PRUEFUNG;
            case RECHNUNGSAUSGANG, RECHNUNGSAUSGANG_ZUR_PRUEFUNG -> RECHNUNGSAUSGANG_ZUR_PRUEFUNG;
            case SONSTIGE -> SONSTIGE;
        };
    }
}
