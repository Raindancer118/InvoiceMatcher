package de.pegasus.invoicematcher.masterdata.account;

/**
 * Die Kontierung einer einzelnen Position.
 *
 * @param sachkonto        Ziel-Sachkonto
 * @param automatic        Automatikkonto - der Steuerschluessel bleibt dann leer
 * @param steuerschluessel 8 (7 %) bzw. 9 (19 %); {@code null} bei Automatikkonten
 * @param confident        {@code false}, wenn nur ein Sammelkonto oder eine Annahme griff
 * @param reason           Begruendung fuer die Pruefung durch einen Menschen
 */
public record AccountAssignment(int sachkonto,
                                boolean automatic,
                                Integer steuerschluessel,
                                boolean confident,
                                String reason) {
}
