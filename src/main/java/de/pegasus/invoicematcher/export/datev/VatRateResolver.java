package de.pegasus.invoicematcher.export.datev;

import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.domain.BookingLine;
import de.pegasus.invoicematcher.domain.TaxKeys;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Ermittelt den Steuersatz einer Buchungszeile.
 *
 * <p>Das interne Zwischenformat traegt bewusst nur den Steuerschluessel, wie im Regelwerk
 * festgelegt. Fuer DATEV wird jedoch der Bruttobetrag gebraucht, und dafuer der Satz. Er laesst
 * sich aus dem Vorhandenen eindeutig herleiten: aus dem Steuerschluessel, sonst - bei
 * Automatikkonten - aus dem Konto selbst, dem der Satz fest zugeordnet ist.
 */
@Component
public class VatRateResolver {

    private static final BigDecimal RATE_REDUCED = new BigDecimal("7");
    private static final BigDecimal RATE_STANDARD = new BigDecimal("19");

    private final Supplier<AccountingProperties> accountingSettings;

    public VatRateResolver(Supplier<AccountingProperties> accountingSettings) {
        this.accountingSettings = accountingSettings;
    }

    /** Leerer Rueckgabewert heisst: der Satz ist aus den vorliegenden Angaben nicht ableitbar. */
    public Optional<BigDecimal> rateOf(BookingLine line) {
        return fromTaxKey(line.steuerschluessel()).or(() -> fromAccount(line.sachkonto()));
    }

    private Optional<BigDecimal> fromTaxKey(Integer taxKey) {
        if (taxKey == null) {
            return Optional.empty();
        }
        return switch (taxKey) {
            case TaxKeys.KEY_REDUCED -> Optional.of(RATE_REDUCED);
            case TaxKeys.KEY_STANDARD -> Optional.of(RATE_STANDARD);
            default -> Optional.empty();
        };
    }

    /** Automatikkonten wie 8400 oder 3300 tragen den Steuersatz in ihrer Kontodefinition. */
    private Optional<BigDecimal> fromAccount(int account) {
        AccountingProperties properties = accountingSettings.get();
        return java.util.stream.Stream.concat(properties.revenue().stream(), properties.defaultExpense().stream())
                .filter(candidate -> candidate.account() == account)
                .findFirst()
                .map(AccountingProperties.VatAccount::vatRate);
    }
}
