package de.pegasus.invoicematcher.masterdata.account;

import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.InvoiceLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Ausgangsrechnungen werden nicht nach Positionen aufgeteilt, sondern ausschliesslich nach
 * Steuersatz (Punkt 7 des Regelwerks).
 */
@Component
public class RevenueAccountResolver implements AccountResolver {

    private static final int ORDER = 10;

    private final AccountingProperties properties;

    public RevenueAccountResolver(AccountingProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<AccountAssignment> resolve(InvoiceLine line, Direction direction) {
        if (direction != Direction.AUSGANG) {
            return Optional.empty();
        }
        BigDecimal rate = line.vatRate();
        return properties.revenue().stream()
                .filter(candidate -> rate != null && candidate.vatRate().compareTo(rate) == 0)
                .findFirst()
                .map(candidate -> new AccountAssignment(candidate.account(), candidate.automatic(),
                        TaxKeys.forRate(rate, candidate.automatic()), true,
                        "Erloeskonto nach Steuersatz " + rate + " %"));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
