package de.pegasus.invoicematcher.extraction.ocr;

import de.pegasus.invoicematcher.extraction.text.DocumentText;
import de.pegasus.invoicematcher.extraction.text.DocumentTitleExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;

/**
 * Fragt eine zweite Zeichenerkennung nach, wenn die erste keinen bekannten Belegtitel gefunden
 * hat.
 *
 * <p>Handschriftliche Belege sind der Fall, fuer den das noetig ist: Tesseract ist auf
 * Druckschrift trainiert und verliest handschriftliche Ueberschriften regelmaessig zu
 * Buchstabensalat (z.B. "Rechnung" zu "-echeinee r") - der Beleg landet dann unter "Sonstige",
 * obwohl der Titel im Original eindeutig ist. Angefragt wird nur bei diesem konkreten Verdacht:
 * die zweite Erkennung ist typischerweise ein Cloud-Dienst, und jede unnoetige Anfrage waere
 * unnoetiger Versand des Belegbilds und unnoetige Kosten.
 */
public class FallbackOcrTextRecognizer implements OcrTextRecognizer {

    private static final Logger log = LoggerFactory.getLogger(FallbackOcrTextRecognizer.class);

    private final OcrTextRecognizer primary;
    private final OcrTextRecognizer secondary;
    private final DocumentTitleExtractor titleExtractor = new DocumentTitleExtractor();

    public FallbackOcrTextRecognizer(OcrTextRecognizer primary, OcrTextRecognizer secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public boolean isAvailable() {
        return primary.isAvailable() || secondary.isAvailable();
    }

    @Override
    public String recognize(BufferedImage page) {
        String primaryText = primary.isAvailable() ? primary.recognize(page) : "";
        if (hasRecognisableTitle(primaryText) || !secondary.isAvailable()) {
            return primaryText;
        }
        log.info("Kein Belegtitel erkannt - Seite geht zusaetzlich an die zweite Zeichenerkennung ({})",
                secondary);
        try {
            String secondaryText = secondary.recognize(page);
            if (hasRecognisableTitle(secondaryText) || primaryText.isBlank()) {
                return secondaryText;
            }
            return primaryText;
        } catch (RuntimeException e) {
            log.warn("Zweite Zeichenerkennung fehlgeschlagen, bleibe beim ersten Ergebnis: {}", e.getMessage());
            return primaryText;
        }
    }

    private boolean hasRecognisableTitle(String text) {
        return titleExtractor.extract(DocumentText.of(text)).isPresent();
    }
}
