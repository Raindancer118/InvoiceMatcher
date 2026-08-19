package de.pegasus.invoicematcher.rules;

import de.pegasus.invoicematcher.domain.BookingLine;
import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.ExtractedInvoice;
import de.pegasus.invoicematcher.domain.Finding;
import de.pegasus.invoicematcher.domain.InvoiceLine;
import de.pegasus.invoicematcher.masterdata.account.AccountAssignment;
import de.pegasus.invoicematcher.masterdata.account.AccountAssignmentService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bildet aus Rechnungspositionen die Buchungszeilen.
 *
 * <p>Positionen mit demselben Sachkonto <em>und</em> demselben Steuersatz werden zu einer Zeile
 * zusammengefasst, die Nettobetraege addiert. Nur unterschiedliches Konto oder unterschiedlicher
 * Steuersatz erzeugen eine eigene Zeile. Ausgangsrechnungen kennen keine Positionsaufteilung -
 * dort entsteht genau eine Zeile je Steuersatz.
 */
@Component
public class BookingLineAggregator {

    /** DATEV begrenzt den Buchungstext; laengere Texte werden gekuerzt. */
    private static final int MAX_BUCHUNGSTEXT_LENGTH = 60;

    /** Abschlag je Position, deren Kontierung nicht belastbar ist. */
    private static final double UNCERTAIN_ACCOUNT_PENALTY = 0.05;
    private static final double MAX_ACCOUNT_PENALTY = 0.20;

    private final AccountAssignmentService accounts;

    public BookingLineAggregator(AccountAssignmentService accounts) {
        this.accounts = accounts;
    }

    public Result aggregate(ExtractedInvoice invoice, Direction direction) {
        List<Finding> findings = new ArrayList<>();
        List<InvoiceLine> positions = positionsOf(invoice, findings);
        if (positions.isEmpty()) {
            return new Result(List.of(), findings, 0.0);
        }

        Map<GroupKey, Group> groups = new LinkedHashMap<>();
        double penalty = 0.0;
        for (InvoiceLine position : positions) {
            AccountAssignment assignment = accounts.assign(position, direction);
            if (!assignment.confident()) {
                penalty = Math.min(MAX_ACCOUNT_PENALTY, penalty + UNCERTAIN_ACCOUNT_PENALTY);
                findings.add(Finding.warn("3.3", "Position '" + position.description() + "': "
                        + assignment.reason()));
            }
            groups.computeIfAbsent(GroupKey.of(assignment, position), key -> new Group(assignment))
                    .add(position);
        }
        List<BookingLine> lines = groups.values().stream()
                .map(group -> group.toBookingLine(direction))
                .toList();
        return new Result(lines, findings, penalty);
    }

    /**
     * Ohne lesbare Positionen wird ersatzweise der ausgewiesene Nettobetrag als eine Position
     * behandelt. Das haelt den Beleg buchbar, wird aber ausdruecklich vermerkt.
     */
    private List<InvoiceLine> positionsOf(ExtractedInvoice invoice, List<Finding> findings) {
        if (!invoice.lines().isEmpty()) {
            return invoice.lines();
        }
        BigDecimal net = invoice.totals().net();
        if (net == null) {
            return List.of();
        }
        findings.add(Finding.warn("3.1",
                "Keine Positionen lesbar - der Beleg wird mit dem ausgewiesenen Nettobetrag gebucht"));
        return List.of(new InvoiceLine(fallbackText(invoice), BigDecimal.ONE, net, net, vatRateOf(invoice)));
    }

    private static String fallbackText(ExtractedInvoice invoice) {
        return invoice.title().orElse("Rechnungsbetrag");
    }

    /** Leitet den Steuersatz aus Steuer- und Nettobetrag ab, wenn keine Position ihn nennt. */
    private static BigDecimal vatRateOf(ExtractedInvoice invoice) {
        BigDecimal net = invoice.totals().net();
        BigDecimal tax = invoice.totals().tax();
        if (net == null || tax == null || net.signum() == 0) {
            return null;
        }
        return tax.multiply(BigDecimal.valueOf(100)).divide(net, 0, RoundingMode.HALF_UP);
    }

    /** Zusammenfassungsschluessel: Sachkonto und Steuersatz, wie im Regelwerk gefordert. */
    private record GroupKey(int sachkonto, BigDecimal vatRate) {

        static GroupKey of(AccountAssignment assignment, InvoiceLine line) {
            return new GroupKey(assignment.sachkonto(),
                    line.vatRate() == null ? null : line.vatRate().stripTrailingZeros());
        }
    }

    private static final class Group {

        private final AccountAssignment assignment;
        private final List<String> descriptions = new ArrayList<>();
        private BigDecimal net = BigDecimal.ZERO;

        private Group(AccountAssignment assignment) {
            this.assignment = assignment;
        }

        void add(InvoiceLine line) {
            if (line.netAmount() != null) {
                net = net.add(line.netAmount());
            }
            if (line.description() != null && !descriptions.contains(line.description())) {
                descriptions.add(line.description());
            }
        }

        BookingLine toBookingLine(Direction direction) {
            return new BookingLine(assignment.sachkonto(), BookingLine.kennzeichenFor(direction),
                    assignment.steuerschluessel(), net.setScale(2, RoundingMode.HALF_UP), buchungstext());
        }

        private String buchungstext() {
            String text = String.join(", ", descriptions);
            return text.length() <= MAX_BUCHUNGSTEXT_LENGTH
                    ? text
                    : text.substring(0, MAX_BUCHUNGSTEXT_LENGTH - 1).stripTrailing() + "…";
        }
    }

    /**
     * @param lines    die gebildeten Buchungszeilen
     * @param findings Hinweise zur Kontierung
     * @param penalty  Konfidenzabschlag aus unsicheren Kontierungen
     */
    public record Result(List<BookingLine> lines, List<Finding> findings, double penalty) {
    }
}
