package de.pegasus.invoicematcher.classification.ai;

import de.pegasus.invoicematcher.classification.ClassificationRationale;
import de.pegasus.invoicematcher.domain.ClassificationResult;
import de.pegasus.invoicematcher.domain.DocumentType;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.extraction.ExtractionService;
import de.pegasus.invoicematcher.support.Belege;
import io.github.raindancer118.aigateway.ChatRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die zweite KI-Anfrage: sie erklaert die bereits getroffene Entscheidung, aendert sie aber nie.
 * Faellt sie aus oder antwortet unbrauchbar, bleibt der Beleg trotzdem vollstaendig kontiert -
 * eine fehlende Erlaeuterung darf niemals eine Buchung verhindern.
 */
class ClassificationRationaleServiceTest {

    private final ExtractedInvoice invoice = ExtractionService.standard()
            .extract(Belege.document("01_PDF_Klaus_Furniere.pdf"));

    private final ClassificationResult result = new ClassificationResult(DocumentType.RECHNUNGSEINGANG,
            "04012026", "5007", 70101, List.of(), null, new BigDecimal("0.95"));

    private static ClassificationRationaleService serviceReturning(String content) {
        return new ClassificationRationaleService(request -> content == null
                ? Optional.empty()
                : Optional.of(new InvoiceClassifier.Result(content, "google-gemini", "gemini-flash-latest")));
    }

    @Test
    void turnsTheModelAnswerIntoAReadableRationale() {
        ClassificationRationaleService service = serviceReturning("""
                {
                  "kurzfassung": "Eindeutige Eingangsrechnung eines bekannten Kreditors.",
                  "schritte": [
                    {"titel": "Belegart", "text": "Titel 'Rechnung', Positionen mit Einzelpreisen."},
                    {"titel": "Personenkonto", "text": "Klaus Furniere steht mit 70101 in der Partnerliste."}
                  ],
                  "unsicherheiten": ["Der Zuschnitt koennte auch auf 3400 statt 3401 gehoeren."],
                  "gegenprobe": "Waere der Aussteller das eigene Unternehmen, waere es ein Rechnungsausgang."
                }
                """);

        ClassificationRationale rationale = service.explain(invoice, result, List.of()).orElseThrow();

        assertThat(rationale.model()).isEqualTo("google-gemini/gemini-flash-latest");
        assertThat(rationale.kurzfassung()).contains("Eingangsrechnung");
        assertThat(rationale.schritte()).hasSize(2);
        assertThat(rationale.schritte().get(0).titel()).isEqualTo("Belegart");
        assertThat(rationale.schritte().get(1).text()).contains("70101");
        assertThat(rationale.unsicherheiten()).singleElement().asString().contains("3400");
        assertThat(rationale.gegenprobe()).contains("Rechnungsausgang");
    }

    @Test
    void acceptsAnAnswerWrappedInAMarkdownCodeFence() {
        ClassificationRationaleService service = serviceReturning("""
                Gern, hier meine Herleitung:
                ```json
                {"kurzfassung": "Kurz und knapp.", "schritte": [], "unsicherheiten": [], "gegenprobe": null}
                ```
                """);

        assertThat(service.explain(invoice, result, List.of()).orElseThrow().kurzfassung())
                .isEqualTo("Kurz und knapp.");
    }

    @Test
    void fallsBackToThePlainTextWhenTheModelIgnoresTheJsonFormat() {
        ClassificationRationaleService service = serviceReturning(
                "Der Beleg ist eindeutig eine Eingangsrechnung, weil alle Pflichtangaben vorhanden sind.");

        ClassificationRationale rationale = service.explain(invoice, result, List.of()).orElseThrow();

        assertThat(rationale.kurzfassung()).contains("Pflichtangaben");
        assertThat(rationale.schritte()).isEmpty();
    }

    @Test
    void staysEmptyWhenNoModelIsAvailable() {
        assertThat(serviceReturning(null).explain(invoice, result, List.of())).isEmpty();
    }

    @Test
    void asksAboutTheDecisionThatWasAlreadyMadeInsteadOfRequestingANewOne() {
        AtomicReference<ChatRequest> captured = new AtomicReference<>();
        ClassificationRationaleService service = new ClassificationRationaleService(request -> {
            captured.set(request);
            return Optional.of(new InvoiceClassifier.Result("{\"kurzfassung\": \"x\"}", "fake", "fake-model"));
        });

        service.explain(invoice, result, List.of());

        String prompt = captured.get().messages().get(0).content();
        assertThat(prompt).contains("Rechnungseingang").contains("70101").contains("5007");
        assertThat(prompt).doesNotContain("konfidenz_score\": <");
    }
}
