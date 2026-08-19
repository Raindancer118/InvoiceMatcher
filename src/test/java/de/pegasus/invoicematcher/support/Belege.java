package de.pegasus.invoicematcher.support;

import de.pegasus.invoicematcher.extraction.SourceDocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

/** Zugriff auf die 30 Testbelege unter {@code src/test/resources/belege}. */
public final class Belege {

    private static final String ROOT = "/belege/";

    private Belege() {
    }

    public static byte[] bytes(String filename) {
        try (InputStream in = Belege.class.getResourceAsStream(ROOT + filename)) {
            if (in == null) {
                throw new IllegalArgumentException("Testbeleg nicht gefunden: " + filename);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SourceDocument document(String filename) {
        return new SourceDocument(filename, bytes(filename));
    }

    /** Alle Belegdateien in fachlicher Reihenfolge (01 bis 31). */
    public static List<String> all() {
        return Stream.concat(
                        Stream.of("01_PDF_Klaus_Furniere.pdf", "02_PDF_Saege_Platte.pdf",
                                "03_PDF_Redloff_Plattenwerkstoff.pdf", "04_PDF_Farben_Lack.pdf",
                                "05_PDF_Buerobedarf_Kopierer.pdf", "06_PDF_Tankstelle_Diesel.pdf",
                                "07_ZUGFeRD_Kreditortestrium_Furniere.pdf", "08_ZUGFeRD_Saegewerk_Testtanne.pdf",
                                "09_ZUGFeRD_Unkastester_Furniere.pdf", "10_ZUGFeRD_Farben_Lack.pdf",
                                "11_ZUGFeRD_Redloff_Platte.pdf", "12_ZUGFeRD_Schmitt_Platte.pdf",
                                "13_XRechnung_Spanexempel_gemischt.xml", "14_XRechnung_Saegewerk_Testtanne.xml",
                                "15_XRechnung_Farben_Lack.xml", "16_AR_Laub.pdf", "17_AR_Beispiel.pdf"),
                        Stream.of("18_Sonstige_Mietvertrag.pdf", "19_Sonstige_Grundabgabenbescheid.pdf",
                                "20_PDF_Ton_unbekannterPartner.pdf",
                                "21_konstruiert_kein_Titel_Auftragsbestaetigung.pdf",
                                "22_konstruiert_kein_Titel_Lieferschein.pdf",
                                "23_konstruiert_kein_Titel_Angebot.pdf",
                                "24_konstruiert_fehlende_Rechnungsnr.pdf",
                                "25_konstruiert_fehlendes_Datum.pdf",
                                "26_konstruiert_fehlende_UStIdNr.pdf",
                                "27_konstruiert_fehlender_Nettobetrag.pdf",
                                "28_konstruiert_fehlender_Steuerbetrag.pdf",
                                "29_konstruiert_Rechenfehler.pdf",
                                "30_konstruiert_Gutschrift_positiv.pdf",
                                "31_konstruiert_7_Prozent_USt.pdf"))
                .toList();
    }
}
