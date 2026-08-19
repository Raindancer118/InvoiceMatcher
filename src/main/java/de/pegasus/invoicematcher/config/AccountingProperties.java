package de.pegasus.invoicematcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Der fachliche Kontierungsrahmen - Kontonummern, Kategorien und Schwellenwerte.
 *
 * <p>Bewusst Konfiguration statt Code: der Kontenrahmen unterscheidet sich je Mandant, und
 * das Ergaenzen einer Warengruppe darf kein Deployment erfordern.
 *
 * @param gwgLimit           Nettoeinzelpreis, ab dem eine Anlagenposition zur Pruefung geht
 * @param roundingTolerance  zulaessige Differenz der Rechenprobe (Regel 1.3)
 * @param revenue            Erloeskonten je Steuersatz (Ausgangsrechnung)
 * @param defaultExpense     Sammelkonten je Steuersatz, wenn keine Kategorie greift
 * @param categories         Warengruppen mit ihren Positionstext-Stichwoertern
 * @param fixedAssetKeywords Stichwoerter, die auf Anlagevermoegen hindeuten
 */
@ConfigurationProperties("invoicematcher.accounting")
public record AccountingProperties(BigDecimal gwgLimit,
                                   BigDecimal roundingTolerance,
                                   List<VatAccount> revenue,
                                   List<VatAccount> defaultExpense,
                                   List<ExpenseCategory> categories,
                                   List<String> fixedAssetKeywords) {

    /** Ein Konto, das ausschliesslich ueber den Steuersatz bestimmt wird. */
    public record VatAccount(BigDecimal vatRate, int account, boolean automatic) {
    }

    /**
     * Eine Warengruppe.
     *
     * @param automatic Automatikkonten steuern den Steuerschluessel selbst; dann bleibt er leer
     */
    public record ExpenseCategory(String label, int account, boolean automatic, List<String> keywords) {
    }
}
