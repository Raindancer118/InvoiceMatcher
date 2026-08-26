package de.pegasus.invoicematcher.extraction.ocr;

import de.pegasus.invoicematcher.extraction.text.PdfBoxTextSource;
import de.pegasus.invoicematcher.extraction.text.PdfText;
import de.pegasus.invoicematcher.support.Belege;
import de.pegasus.invoicematcher.support.Scans;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OcrPdfTextSourceTest {

    private static final int MIN_CHARACTERS = 40;

    private final StubRecognizer recognizer = new StubRecognizer();

    private OcrPdfTextSource source() {
        return new OcrPdfTextSource(new PdfBoxTextSource(), new PdfPageRenderer(100, 5),
                recognizer, MIN_CHARACTERS);
    }

    @Test
    void usesTheTextLayerOfARegularPdfAndNeverStartsTheRecognizer() {
        PdfText text = source().extractText(Belege.bytes("01_PDF_Klaus_Furniere.pdf"));

        assertThat(text.recognized()).isFalse();
        assertThat(text.text()).contains("Friedrich Klausmuster");
        assertThat(recognizer.pages).isEmpty();
    }

    @Test
    void recognizesAScannedPdfPageByPage() {
        byte[] scan = Scans.flatten(Belege.bytes("01_PDF_Klaus_Furniere.pdf"), 100);

        PdfText text = source().extractText(scan);

        assertThat(recognizer.pages).hasSize(1);
        assertThat(text.recognized()).isTrue();
        assertThat(text.text()).contains(StubRecognizer.RESULT);
    }

    @Test
    void keepsTheEmptyTextLayerWhenNoRecognizerIsInstalled() {
        recognizer.available = false;
        byte[] scan = Scans.flatten(Belege.bytes("01_PDF_Klaus_Furniere.pdf"), 100);

        PdfText text = source().extractText(scan);

        assertThat(text.recognized()).isFalse();
        assertThat(text.text().chars().filter(Character::isLetterOrDigit).count()).isLessThan(MIN_CHARACTERS);
    }

    private static final class StubRecognizer implements OcrTextRecognizer {

        static final String RESULT = "Rechnung   Rechnungsnummer 5007";

        private final List<BufferedImage> pages = new ArrayList<>();
        private boolean available = true;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String recognize(BufferedImage page) {
            pages.add(page);
            return RESULT;
        }
    }
}
