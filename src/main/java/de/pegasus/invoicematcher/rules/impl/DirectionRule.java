package de.pegasus.invoicematcher.rules.impl;

import de.pegasus.invoicematcher.config.CompanyProperties;
import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.masterdata.MatchingText;
import de.pegasus.invoicematcher.rules.ClassificationContext;
import de.pegasus.invoicematcher.rules.IRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Bestimmt, ob der Beleg eine Eingangs- oder eine Ausgangsrechnung ist.
 *
 * <p>Keine nummerierte Regel des Regelwerks, aber Voraussetzung fuer alle folgenden: erst die
 * Richtung entscheidet, ob ein Kreditor oder ein Debitor gesucht wird und ob nach Positionen
 * oder nach Steuersatz kontiert wird.
 */
@Component
@Order(DirectionRule.ORDER)
public class DirectionRule implements IRule {

    static final int ORDER = 200;

    /** Abschlag, wenn die Richtung nur unterstellt und nicht belegt werden konnte. */
    private static final double UNCERTAIN_DIRECTION_PENALTY = 0.10;

    private final CompanyProperties company;

    public DirectionRule(CompanyProperties company) {
        this.company = company;
    }

    @Override
    public String id() {
        return "2.0";
    }

    @Override
    public String description() {
        return "Belegrichtung anhand des eigenen Unternehmens bestimmen";
    }

    @Override
    public void apply(ClassificationContext context) {
        if (company.matches(context.invoice().seller())) {
            context.direction(Direction.AUSGANG);
            return;
        }
        context.direction(Direction.EINGANG);
        if (company.matches(context.invoice().buyer()) || mentionsOwnCompany(context)) {
            return;
        }
        context.penalize(UNCERTAIN_DIRECTION_PENALTY, Finding.warn(id(),
                "Weder Aussteller noch Empfaenger konnten dem eigenen Unternehmen zugeordnet werden"
                        + " - es wird eine Eingangsrechnung unterstellt"));
    }

    /**
     * Nicht jeder Beleg beschriftet sein Anschriftenfeld. Steht der eigene Firmenname aber
     * ueberhaupt im Dokument und ist nicht der Aussteller, ist es eine Eingangsrechnung.
     */
    private boolean mentionsOwnCompany(ClassificationContext context) {
        return MatchingText.contains(context.invoice().rawText(), company.name());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
