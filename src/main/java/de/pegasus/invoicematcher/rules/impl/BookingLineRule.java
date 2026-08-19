package de.pegasus.invoicematcher.rules.impl;

import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.domain.BookingLine;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.rules.BookingLineAggregator;
import de.pegasus.invoicematcher.rules.ClassificationContext;
import de.pegasus.invoicematcher.rules.IRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Punkt 7: Sachkontenzuordnung und Zusammenfassung zu Buchungszeilen.
 *
 * <p>Laeuft nur, wenn kein vorheriger Regelverstoss den Beleg zur Pruefung geschickt hat -
 * ein bemaengelter Beleg wird ohne Buchungsdaten weitergegeben.
 */
@Component
@Order(BookingLineRule.ORDER)
public class BookingLineRule implements IRule {

    static final int ORDER = 700;

    private final BookingLineAggregator aggregator;
    private final AccountingProperties properties;

    public BookingLineRule(BookingLineAggregator aggregator, AccountingProperties properties) {
        this.aggregator = aggregator;
        this.properties = properties;
    }

    @Override
    public String id() {
        return "3.0";
    }

    @Override
    public String description() {
        return "Buchungszeilen aus den Positionen bilden";
    }

    @Override
    public void apply(ClassificationContext context) {
        if (!context.documentType().carriesBookingData()) {
            return;
        }
        BookingLineAggregator.Result result = aggregator.aggregate(context.invoice(), context.direction());
        context.bookingLines(result.lines());
        result.findings().forEach(context::addFinding);
        context.penalize(result.penalty());
        verifyAgainstDeclaredTotal(context, result.lines());
    }

    /** Die Summe der Buchungszeilen muss dem ausgewiesenen Nettobetrag entsprechen. */
    private void verifyAgainstDeclaredTotal(ClassificationContext context, List<BookingLine> lines) {
        BigDecimal declaredNet = context.invoice().totals().net();
        if (declaredNet == null || lines.isEmpty()) {
            return;
        }
        BigDecimal booked = lines.stream().map(BookingLine::umsatz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deviation = booked.subtract(declaredNet).abs();
        if (deviation.compareTo(properties.roundingTolerance()) > 0) {
            context.penalize(0.15, Finding.warn("3.2", String.format(
                    "Summe der Buchungszeilen (%s EUR) weicht vom ausgewiesenen Nettobetrag (%s EUR) ab",
                    booked, declaredNet)));
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
