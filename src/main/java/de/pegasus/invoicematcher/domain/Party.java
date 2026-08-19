package de.pegasus.invoicematcher.domain;

/** Ein Beleg-Beteiligter mit den Identifikatoren, die fuer die Kontenzuordnung zaehlen. */
public record Party(String name, String vatId, String taxNumber) {

    public static final Party UNKNOWN = new Party(null, null, null);

    public boolean hasName() {
        return name != null && !name.isBlank();
    }

    /** Rechtlich genuegt eine der beiden Nummern (§14 Abs. 4 Nr. 2 UStG). */
    public boolean hasTaxIdentifier() {
        return isPresent(vatId) || isPresent(taxNumber);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
