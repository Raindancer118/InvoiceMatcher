package de.pegasus.invoicematcher.masterdata.account;

import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.InvoiceLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Letzte Instanz der Kontenfindung: das Wareneingangs-Sammelkonto zum Steuersatz.
 *
 * <p>Liefert bewusst {@code confident = false} - die Zuordnung ist formal richtig, fachlich
 * aber ungeprueft und soll im Review sichtbar sein.
 */
@Component
public class DefaultExpenseAccountResolver implements AccountResolver {

    private static final int ORDER = 90;

    /** Ohne ausgewiesenen Steuersatz wird der Regelsteuersatz unterstellt. */
    private static final BigDecimal ASSUMED_RATE = new BigDecimal("19");

    private final AccountingProperties properties;

    public DefaultExpenseAccountResolver(AccountingProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<AccountAssignment> resolve(InvoiceLine line, Direction direction) {
        if (direction != Direction.EINGANG) {
            return Optional.empty();
        }
        BigDecimal rate = line.hasVatRate() ? line.vatRate() : ASSUMED_RATE;
        return properties.defaultExpense().stream()
                .filter(candidate -> candidate.vatRate().compareTo(rate) == 0)
                .findFirst()
                .map(candidate -> new AccountAssignment(candidate.account(), candidate.automatic(),
                        TaxKeys.forRate(rate, candidate.automatic()), false,
                        "Positionstext passt auf keine Kategorie - Sammelkonto zum Steuersatz " + rate + " %"));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
