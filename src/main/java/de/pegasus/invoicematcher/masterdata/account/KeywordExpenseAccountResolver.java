package de.pegasus.invoicematcher.masterdata.account;

import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.InvoiceLine;
import de.pegasus.invoicematcher.masterdata.MatchingText;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

/**
 * Ordnet eine Eingangsrechnungsposition ueber ihren Positionstext einer Warengruppe zu.
 *
 * <p>Der Positionstext ist die einzige belastbare Grundlage: ein Lieferant namens "Saegewerk"
 * liefert nachweislich beschichtete Plattenware, ein Beleg von "Farben-Fritze" kann Bueromaterial
 * enthalten. Bei mehreren Treffern gewinnt das laengste Stichwort, weil es das speziellere ist.
 */
@Component
public class KeywordExpenseAccountResolver implements AccountResolver {

    private static final int ORDER = 20;

    private final AccountingProperties properties;

    public KeywordExpenseAccountResolver(AccountingProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<AccountAssignment> resolve(InvoiceLine line, Direction direction) {
        if (direction != Direction.EINGANG || line.description() == null) {
            return Optional.empty();
        }
        String text = line.description();
        return properties.categories().stream()
                .flatMap(category -> category.keywords().stream()
                        .filter(keyword -> MatchingText.contains(text, keyword))
                        .map(keyword -> new Hit(category, keyword)))
                .max(Comparator.comparingInt(hit -> hit.keyword().length()))
                .map(hit -> toAssignment(hit, line));
    }

    private AccountAssignment toAssignment(Hit hit, InvoiceLine line) {
        AccountingProperties.ExpenseCategory category = hit.category();
        return new AccountAssignment(category.account(), category.automatic(),
                TaxKeys.forRate(line.vatRate(), category.automatic()),
                line.hasVatRate(),
                "Warengruppe '" + category.label() + "' erkannt am Stichwort '" + hit.keyword() + "'");
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private record Hit(AccountingProperties.ExpenseCategory category, String keyword) {
    }
}
