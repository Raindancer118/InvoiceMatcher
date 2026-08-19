package de.pegasus.invoicematcher.extraction.text;

import de.pegasus.invoicematcher.domain.InvoiceLine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest die Positionstabelle.
 *
 * <p>Erkannt wird eine Zeile aus Positionsnummer, Artikelnummer, Bezeichnung, Menge, Einheit,
 * Nettoeinzelpreis, Steuersatz und Zeilensumme. Zeilen ohne Preise - etwa Lieferscheinpositionen
 * oder Fortsetzungszeilen einer Artikelbeschreibung - werden bewusst uebergangen.
 */
public final class LineItemExtractor implements TextFieldExtractor<List<InvoiceLine>> {

    private static final Pattern ROW = Pattern.compile(
            "^\\s*\\d{1,3}\\s+"                              // Positionsnummer
                    + "(\\S+)\\s+"                           // Artikelnummer
                    + "(.+?)\\s+"                            // Artikelbezeichnung
                    + "(\\d+(?:[.,]\\d+)?)\\s+"              // Menge
                    + "(\\S+)\\s+"                           // Einheit
                    + "([\\d.]+,\\d{2})\\s*(?:€|EUR)?\\s+"   // Nettoeinzelpreis
                    + "(\\d{1,2}(?:,\\d{1,2})?)\\s*%\\s+"    // Steuersatz
                    + "([\\d.]+,\\d{2})\\s*(?:€|EUR)?\\s*$");// Zeilensumme

    @Override
    public Optional<List<InvoiceLine>> extract(DocumentText text) {
        List<InvoiceLine> lines = extractAll(text);
        return lines.isEmpty() ? Optional.empty() : Optional.of(lines);
    }

    public List<InvoiceLine> extractAll(DocumentText text) {
        return text.lines().stream()
                .map(ROW::matcher)
                .filter(Matcher::matches)
                .map(LineItemExtractor::toInvoiceLine)
                .toList();
    }

    private static InvoiceLine toInvoiceLine(Matcher row) {
        return new InvoiceLine(
                row.group(2).strip(),
                number(row.group(3)),
                GermanAmounts.parse(row.group(5)).orElse(null),
                GermanAmounts.parse(row.group(7)).orElse(null),
                number(row.group(6)));
    }

    private static BigDecimal number(String german) {
        return new BigDecimal(german.replace(".", "").replace(',', '.'));
    }
}
