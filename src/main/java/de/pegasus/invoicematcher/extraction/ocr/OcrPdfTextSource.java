package de.pegasus.invoicematcher.extraction.ocr;

import de.pegasus.invoicematcher.extraction.text.PdfText;
import de.pegasus.invoicematcher.extraction.text.PdfTextSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Schaltet vor die Textheuristik eine Zeichenerkennung, wenn das PDF keine brauchbare
 * Textebene hat.
 *
 * <p>Ein gescannter Beleg ist technisch ein gueltiges PDF; PDFBox liefert dafuer aber nichts
 * oder nur die Kopfzeile des Scanners. Ohne diese Stufe liefe ein solcher Beleg mit leeren
 * Feldern durch das Regelwerk und wuerde als unvollstaendige Rechnung bemaengelt - ein Befund
 * ueber den Scanner, nicht ueber den Beleg.
 *
 * <p>Entschieden wird nach der Textmenge je Seite, nicht nach dem Vorhandensein von Text
 * ueberhaupt: viele Scanner legen eine leere Textebene oder eine Geraetekennung an.
 */
public class OcrPdfTextSource implements PdfTextSource {

    private static final Logger log = LoggerFactory.getLogger(OcrPdfTextSource.class);

    private final PdfTextSource embedded;
    private final PdfPageRenderer renderer;
    private final OcrTextRecognizer recognizer;
    private final int minCharactersPerPage;

    public OcrPdfTextSource(PdfTextSource embedded, PdfPageRenderer renderer,
                            OcrTextRecognizer recognizer, int minCharactersPerPage) {
        this.embedded = embedded;
        this.renderer = renderer;
        this.recognizer = recognizer;
        this.minCharactersPerPage = minCharactersPerPage;
    }

    @Override
    public PdfText extractText(byte[] pdf) {
        PdfText fromLayer = embedded.extractText(pdf);
        if (isSubstantial(fromLayer.text())) {
            return fromLayer;
        }
        if (!recognizer.isAvailable()) {
            log.warn("Beleg ohne Textebene, aber keine Zeichenerkennung verfuegbar - "
                    + "der Beleg geht mit leeren Feldern zur Pruefung");
            return fromLayer;
        }
        return PdfText.recognized(recognize(pdf));
    }

    /**
     * Gemessen wird an Buchstaben und Ziffern: Steuerzeichen und Trennlinien eines Scanners
     * sind kein Belegtext, wuerden eine Laengenpruefung aber bestehen.
     */
    private boolean isSubstantial(String text) {
        long characters = text.chars().filter(Character::isLetterOrDigit).count();
        return characters >= (long) minCharactersPerPage;
    }

    private String recognize(byte[] pdf) {
        List<BufferedImage> pages = renderer.render(pdf);
        StringBuilder text = new StringBuilder();
        for (BufferedImage page : pages) {
            text.append(recognizer.recognize(page));
            text.append('\n');
        }
        log.info("Beleg ohne Textebene: {} Seite(n) durch Zeichenerkennung gelesen", pages.size());
        return text.toString();
    }
}
