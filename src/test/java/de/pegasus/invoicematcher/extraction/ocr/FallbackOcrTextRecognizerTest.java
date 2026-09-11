package de.pegasus.invoicematcher.extraction.ocr;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackOcrTextRecognizerTest {

    private static BufferedImage page() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void neverAsksTheSecondEngineWhenTheFirstFoundARecognisableTitle() {
        StubRecognizer primary = StubRecognizer.returning("Rechnung   Rechnungsnummer 5007");
        StubRecognizer secondary = StubRecognizer.returning("egal");

        String text = new FallbackOcrTextRecognizer(primary, secondary).recognize(page());

        assertThat(text).isEqualTo("Rechnung   Rechnungsnummer 5007");
        assertThat(secondary.calls).isEmpty();
    }

    @Test
    void asksTheSecondEngineWhenTheFirstGarbledTheTitle() {
        StubRecognizer primary = StubRecognizer.returning("-echeinee r\n\nVon : Mir");
        StubRecognizer secondary = StubRecognizer.returning("Rechnung\n\nVon : Mir");

        String text = new FallbackOcrTextRecognizer(primary, secondary).recognize(page());

        assertThat(text).isEqualTo("Rechnung\n\nVon : Mir");
        assertThat(secondary.calls).hasSize(1);
    }

    @Test
    void keepsTheFirstResultWhenTheSecondEngineIsNotAvailable() {
        StubRecognizer primary = StubRecognizer.returning("-echeinee r");
        StubRecognizer secondary = StubRecognizer.returning("Rechnung");
        secondary.available = false;

        String text = new FallbackOcrTextRecognizer(primary, secondary).recognize(page());

        assertThat(text).isEqualTo("-echeinee r");
        assertThat(secondary.calls).isEmpty();
    }

    @Test
    void keepsTheFirstResultWhenTheSecondEngineAlsoFindsNoTitle() {
        StubRecognizer primary = StubRecognizer.returning("-echeinee r");
        StubRecognizer secondary = StubRecognizer.returning("still garbage");

        String text = new FallbackOcrTextRecognizer(primary, secondary).recognize(page());

        assertThat(text).isEqualTo("-echeinee r");
    }

    @Test
    void keepsTheFirstResultWhenTheSecondEngineFails() {
        StubRecognizer primary = StubRecognizer.returning("-echeinee r");
        StubRecognizer secondary = StubRecognizer.throwing();

        String text = new FallbackOcrTextRecognizer(primary, secondary).recognize(page());

        assertThat(text).isEqualTo("-echeinee r");
    }

    @Test
    void isAvailableIfEitherEngineIsAvailable() {
        StubRecognizer primary = StubRecognizer.returning("x");
        primary.available = false;
        StubRecognizer secondary = StubRecognizer.returning("y");

        assertThat(new FallbackOcrTextRecognizer(primary, secondary).isAvailable()).isTrue();

        secondary.available = false;
        assertThat(new FallbackOcrTextRecognizer(primary, secondary).isAvailable()).isFalse();
    }

    private static final class StubRecognizer implements OcrTextRecognizer {

        private final List<BufferedImage> calls = new ArrayList<>();
        private boolean available = true;
        private String result;
        private boolean fails;

        static StubRecognizer returning(String result) {
            StubRecognizer recognizer = new StubRecognizer();
            recognizer.result = result;
            return recognizer;
        }

        static StubRecognizer throwing() {
            StubRecognizer recognizer = new StubRecognizer();
            recognizer.fails = true;
            return recognizer;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String recognize(BufferedImage page) {
            calls.add(page);
            if (fails) {
                throw new OcrException("kaputt");
            }
            return result;
        }
    }
}
