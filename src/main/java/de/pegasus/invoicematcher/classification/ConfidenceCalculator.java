package de.pegasus.invoicematcher.classification;

import de.pegasus.invoicematcher.rules.ClassificationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Berechnet den Konfidenzwert eines Klassifikationsergebnisses.
 *
 * <p>Der Wert beantwortet genau eine Frage: wie sicher darf dieses Ergebnis ohne menschliche
 * Pruefung weiterverarbeitet werden? Er ist damit die Stellschraube des Human-in-the-Loop und
 * kein Qualitaetsurteil ueber den Beleg.
 */
@Component
public class ConfidenceCalculator {

    /** Textheuristik ist strukturierten Rechnungsdaten grundsaetzlich unterlegen. */
    private static final double UNSTRUCTURED_SOURCE_PENALTY = 0.10;

    /** Ein Beleg zur Pruefung ist per Definition nicht automatisch verarbeitbar. */
    private static final BigDecimal REVIEW_CEILING = new BigDecimal("0.50");

    public BigDecimal calculate(ClassificationContext context) {
        double score = 1.0 - context.confidencePenalty();
        if (!context.invoice().sourceFormat().isStructured()) {
            score -= UNSTRUCTURED_SOURCE_PENALTY;
        }
        BigDecimal result = BigDecimal.valueOf(Math.clamp(score, 0.0, 1.0))
                .setScale(2, RoundingMode.HALF_UP);
        return context.documentType().isReview() ? result.min(REVIEW_CEILING) : result;
    }
}
