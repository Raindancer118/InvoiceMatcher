package de.pegasus.invoicematcher.masterdata.account;

import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.domain.InvoiceLine;
import de.pegasus.invoicematcher.masterdata.MatchingText;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Erkennt Positionen, die Anlagevermoegen sein koennten und die GWG-Grenze reissen (Punkt 4).
 *
 * <p>Beide Bedingungen muessen zusammenkommen: ein teures Verbrauchsmaterial ist kein
 * Anlagegut, und ein guenstiges Geraet ist ein geringwertiges Wirtschaftsgut und damit
 * regulaer kontierbar. Ausgeloest wird bewusst kein Kontierungsversuch - die Aktivierung ist
 * eine Bilanzentscheidung, keine Standardkontierung.
 */
@Component
public class FixedAssetIndicator {

    private final AccountingProperties properties;

    public FixedAssetIndicator(AccountingProperties properties) {
        this.properties = properties;
    }

    public boolean exceedsGwgThreshold(InvoiceLine line) {
        BigDecimal unitPrice = line.effectiveUnitNetPrice();
        return unitPrice != null
                && unitPrice.compareTo(properties.gwgLimit()) > 0
                && looksLikeFixedAsset(line);
    }

    public boolean looksLikeFixedAsset(InvoiceLine line) {
        if (line.description() == null) {
            return false;
        }
        return properties.fixedAssetKeywords().stream()
                .anyMatch(keyword -> MatchingText.contains(line.description(), keyword));
    }
}
