package de.pegasus.invoicematcher.rules.impl;

import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.domain.Party;
import de.pegasus.invoicematcher.masterdata.partner.PartnerMatch;
import de.pegasus.invoicematcher.masterdata.partner.PartnerResolver;
import de.pegasus.invoicematcher.rules.ClassificationContext;
import de.pegasus.invoicematcher.rules.IRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Punkt 5: Personenkonto aus den Stammdaten, sonst Diverse-Konto nach Anfangsbuchstabe.
 *
 * <p>Ein unbekannter Partner ist ausdruecklich kein Abbruch: der Beleg bleibt buchbar, erhaelt
 * aber einen Kopf-Buchungstext, der die Stammdatenpflege anstoesst.
 */
@Component
@Order(PersonenkontoRule.ORDER)
public class PersonenkontoRule implements IRule {

    static final int ORDER = 600;

    /** Abschlaege je nach Belastbarkeit der Zuordnung. */
    private static final double NAME_MATCH_PENALTY = 0.05;
    private static final double DIVERSE_PENALTY = 0.15;

    private final PartnerResolver resolver;

    public PersonenkontoRule(PartnerResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public String id() {
        return "2.3";
    }

    @Override
    public String description() {
        return "Personenkonto aus den Geschaeftspartner-Stammdaten";
    }

    @Override
    public void apply(ClassificationContext context) {
        Direction direction = context.direction();
        Party counterparty = context.invoice().counterparty(direction);
        PartnerMatch match = resolver.resolve(counterparty, direction);
        context.personenkonto(match.partner().konto());

        switch (match.quality()) {
            case STEUERNUMMER -> { /* eindeutig, kein Abschlag */ }
            case NAME -> context.penalize(NAME_MATCH_PENALTY, Finding.info(id(),
                    "Personenkonto " + match.partner().konto() + " ueber Namensaehnlichkeit zugeordnet ("
                            + match.partner().name() + ")"));
            case DIVERSE -> markUnknownPartner(context, direction, counterparty, match);
        }
    }

    private void markUnknownPartner(ClassificationContext context, Direction direction,
                                    Party counterparty, PartnerMatch match) {
        String role = direction == Direction.AUSGANG ? "Debitor" : "Kreditor";
        String name = counterparty.hasName() ? counterparty.name() : "Name nicht erkannt";
        context.buchungstextKopf(role + " unbekannt – Stammdaten prüfen: " + name);
        context.penalize(DIVERSE_PENALTY, Finding.warn(id(),
                role + " '" + name + "' ist nicht in den Stammdaten - Sammelkonto "
                        + match.partner().konto() + " verwendet"));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
