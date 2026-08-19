package de.pegasus.invoicematcher.rules;

import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Wendet die Ablauflogik in ihrer festgelegten Reihenfolge an.
 *
 * <p>Der Titel-Check bricht ab; alle uebrigen Regeln laufen vollstaendig durch, damit ein
 * Beleg mit mehreren Maengeln auch alle Maengel meldet und nicht nur den ersten.
 */
@Service
public class RuleEngine {

    private final List<IRule> rules;

    public RuleEngine(List<IRule> rules) {
        List<IRule> ordered = new ArrayList<>(rules);
        ordered.sort(AnnotationAwareOrderComparator.INSTANCE);
        this.rules = List.copyOf(ordered);
    }

    public ClassificationContext evaluate(ExtractedInvoice invoice) {
        ClassificationContext context = new ClassificationContext(invoice);
        for (IRule rule : rules) {
            if (context.isStopped()) {
                break;
            }
            rule.apply(context);
        }
        return context;
    }

    public List<IRule> rules() {
        return rules;
    }
}
