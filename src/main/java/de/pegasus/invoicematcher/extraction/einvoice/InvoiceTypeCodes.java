package de.pegasus.invoicematcher.extraction.einvoice;

/**
 * Uebersetzt UNTDID-1001-Typcodes in die Belegbezeichnung, die Regel 1.1 prueft.
 *
 * <p>Strukturierte Formate fuehren keine gedruckte Ueberschrift; der Typcode ist ihr
 * maschinenlesbares Aequivalent.
 */
final class InvoiceTypeCodes {

    private InvoiceTypeCodes() {
    }

    static String toTitle(String typeCode) {
        return switch (typeCode == null ? "" : typeCode.strip()) {
            case "381", "261", "262", "296" -> "Gutschrift";
            case "380", "384", "389", "326", "875", "876", "877" -> "Rechnung";
            default -> null;
        };
    }
}
