package de.pegasus.invoicematcher.support;

/**
 * Vorbereitete KI-Antworten fuer die Testbelege, die Export-, Pruefablauf- und Persistenztests
 * brauchen. Die Werte (Betraege, Konten, Daten) sind aus den echten Testbelegen unter
 * {@code src/test/resources/belege} abgelesen (per {@code pdftotext -layout}), nicht erfunden -
 * diese Tests pruefen Downstream-Verhalten (Export, Pruefablauf, SQLite), nicht die fachliche
 * Richtigkeit einer echten KI-Entscheidung, die dafuer keine Rolle spielt.
 *
 * <p>Jede Antwort wird ueber eine eindeutige, im Prompt vorkommende Kennung ausgeliefert (siehe
 * {@link FakeInvoiceClassifier}) - meist die Rechnungsnummer, bei Formatierungs-Zwillingen
 * (Beleg 01 vs. 29) eine Zahl mit unterschiedlicher Euro-Schreibweise.
 */
public final class CannedAiResponses {

    private CannedAiResponses() {
    }

    public static FakeInvoiceClassifier classifier() {
        return new FakeInvoiceClassifier()
                // 01_PDF_Klaus_Furniere.pdf - Rechnungseingang, bekannter Kreditor
                .respond("5,00 €", """
                        {"document_type": "Rechnungseingang", "belegdatum": "04012026",
                         "belegfeld_1": "5007", "personenkonto": 70101, "personenkonto_unbekannt": false,
                         "buchungszeilen": [
                           {"sachkonto": 3401, "soll_haben_kennzeichen": "S", "steuerschluessel": 9,
                            "umsatz": 399.00, "buchungstext": "Furniere/Kanten"},
                           {"sachkonto": 3400, "soll_haben_kennzeichen": "S", "steuerschluessel": null,
                            "umsatz": 5.00, "buchungstext": "Zuschnitt auf Sondermass"}
                         ], "konfidenz_score": 0.95}
                        """)
                // 04_PDF_Farben_Lack.pdf - Rechnungseingang, bekannter Kreditor
                .respond("1104", """
                        {"document_type": "Rechnungseingang", "belegdatum": "20012026",
                         "belegfeld_1": "1104", "personenkonto": 70104, "personenkonto_unbekannt": false,
                         "buchungszeilen": [
                           {"sachkonto": 3404, "soll_haben_kennzeichen": "S", "steuerschluessel": 9,
                            "umsatz": 1036.87, "buchungstext": "Lacke und Beschichtung"}
                         ], "konfidenz_score": 0.95}
                        """)
                // 05_PDF_Buerobedarf_Kopierer.pdf - GWG-Grenze ueberschritten -> zur Pruefung
                .respond("1088", """
                        {"document_type": "Rechnungseingang zur Prüfung", "belegdatum": "20012026",
                         "belegfeld_1": "1088", "personenkonto": 70105, "personenkonto_unbekannt": false,
                         "buchungszeilen": [], "konfidenz_score": 0.4,
                         "begruendung": "Digitalkopiergeraet ueber der GWG-Grenze - Anlagevermoegen-Entscheidung"}
                        """)
                // 08_ZUGFeRD_Saegewerk_Testtanne.pdf - strukturiert, hohe Konfidenz
                .respond("C-559", """
                        {"document_type": "Rechnungseingang", "belegdatum": "04032026",
                         "belegfeld_1": "C-559", "personenkonto": 70107, "personenkonto_unbekannt": false,
                         "buchungszeilen": [
                           {"sachkonto": 3403, "soll_haben_kennzeichen": "S", "steuerschluessel": 9,
                            "umsatz": 200.00, "buchungstext": "Plattenwerkstoffe"}
                         ], "konfidenz_score": 1.0}
                        """)
                // 09_ZUGFeRD_Unkastester_Furniere.pdf - strukturiert, bekannter Kreditor
                .respond("45679", """
                        {"document_type": "Rechnungseingang", "belegdatum": "22012026",
                         "belegfeld_1": "45679", "personenkonto": 70108, "personenkonto_unbekannt": false,
                         "buchungszeilen": [
                           {"sachkonto": 3401, "soll_haben_kennzeichen": "S", "steuerschluessel": 9,
                            "umsatz": 100.00, "buchungstext": "Furniere/Kanten"}
                         ], "konfidenz_score": 1.0}
                        """)
                // 13_XRechnung_Spanexempel_gemischt.xml - unbekannter Kreditor, zwei Warengruppen
                .respond("7547", """
                        {"document_type": "Rechnungseingang", "belegdatum": "06052026",
                         "belegfeld_1": "7547", "personenkonto": 70008, "personenkonto_unbekannt": false,
                         "buchungszeilen": [
                           {"sachkonto": 3403, "soll_haben_kennzeichen": "S", "steuerschluessel": 9,
                            "umsatz": 82.05, "buchungstext": "Plattenwerkstoffe"},
                           {"sachkonto": 3402, "soll_haben_kennzeichen": "S", "steuerschluessel": 9,
                            "umsatz": 456.67, "buchungstext": "Massiv- und Schnittholz"}
                         ], "konfidenz_score": 0.9}
                        """)
                // 16_AR_Laub.pdf - Ausgangsrechnung, bekannter Debitor
                .respond("20JJ03046", """
                        {"document_type": "Rechnungsausgang", "belegdatum": "03012026",
                         "belegfeld_1": "20JJ03046", "personenkonto": 10101, "personenkonto_unbekannt": false,
                         "buchungszeilen": [
                           {"sachkonto": 8400, "soll_haben_kennzeichen": "H", "steuerschluessel": null,
                            "umsatz": 315.55, "buchungstext": "Erloes"}
                         ], "konfidenz_score": 0.95}
                        """)
                // 18_Sonstige_Mietvertrag.pdf - kein Rechnungscharakter
                .respond("Mietvertrag", """
                        {"document_type": "Sonstige", "belegdatum": null, "belegfeld_1": null,
                         "personenkonto": null, "buchungszeilen": [], "konfidenz_score": 1.0,
                         "begruendung": "Mietvertrag, keine Rechnung oder Gutschrift"}
                        """)
                // 20_PDF_Ton_unbekannterPartner.pdf - unbekannter Kreditor, niedrige Konfidenz
                .respond("60528", """
                        {"document_type": "Rechnungseingang", "belegdatum": "11012026",
                         "belegfeld_1": "60528", "personenkonto": 70004, "personenkonto_unbekannt": false,
                         "buchungstext_kopf": "Kreditor unbekannt – Stammdaten prüfen: Dietrich Tonexempel",
                         "buchungszeilen": [
                           {"sachkonto": 3401, "soll_haben_kennzeichen": "S", "steuerschluessel": 9,
                            "umsatz": 308.18, "buchungstext": "Furniere/Kanten"}
                         ], "konfidenz_score": 0.6}
                        """)
                // 29_konstruiert_Rechenfehler.pdf - Rechenprobe schlaegt fehl -> zur Pruefung
                .respond("5,00 EUR", """
                        {"document_type": "Rechnungseingang zur Prüfung", "belegdatum": "04012026",
                         "belegfeld_1": "5007", "personenkonto": 70101, "personenkonto_unbekannt": false,
                         "buchungszeilen": [], "konfidenz_score": 0.3,
                         "begruendung": "Netto plus Steuer ergibt nicht die angegebene Gesamtsumme"}
                        """)
                // 31_konstruiert_7_Prozent_USt.pdf - reduzierter Steuersatz, bekannter Kreditor
                .respond("1099", """
                        {"document_type": "Rechnungseingang", "belegdatum": "25012026",
                         "belegfeld_1": "1099", "personenkonto": 70105, "personenkonto_unbekannt": false,
                         "buchungszeilen": [
                           {"sachkonto": 4940, "soll_haben_kennzeichen": "S", "steuerschluessel": 8,
                            "umsatz": 74.70, "buchungstext": "Fachliteratur"}
                         ], "konfidenz_score": 0.95}
                        """);
    }
}
