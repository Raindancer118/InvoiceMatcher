package de.pegasus.invoicematcher.masterdata.account;

import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.InvoiceLine;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AccountAssignmentServiceTest {

    private final AccountAssignmentService service = Kontierung.standardAccountAssignment();

    private static InvoiceLine position(String description, String vatRate) {
        return new InvoiceLine(description, BigDecimal.ONE, new BigDecimal("10.00"),
                new BigDecimal("10.00"), new BigDecimal(vatRate));
    }

    @Test
    void assignsIncomingLinesByPositionTextNotBySupplierName() {
        assertThat(service.assign(position("Echtholzfurnierkanten Ahorn 24 mm", "19"), Direction.EINGANG).sachkonto())
                .isEqualTo(3401);
        assertThat(service.assign(position("Arbeitsplatte Eiche 60 cm, beschichtet", "19"), Direction.EINGANG).sachkonto())
                .isEqualTo(3403);
        assertThat(service.assign(position("Holzschutzlasur, Palisander", "19"), Direction.EINGANG).sachkonto())
                .isEqualTo(3404);
        assertThat(service.assign(position("Benzin E10", "19"), Direction.EINGANG).sachkonto())
                .isEqualTo(4530);
        assertThat(service.assign(position("Digitalkopiergerät HS-1233", "19"), Direction.EINGANG).sachkonto())
                .isEqualTo(4930);
    }

    @Test
    void assignsSolidTimberSeparatelyFromPanelMaterial() {
        assertThat(service.assign(position("Eichenbretter 2m", "19"), Direction.EINGANG).sachkonto()).isEqualTo(3402);
        assertThat(service.assign(position("Latten", "19"), Direction.EINGANG).sachkonto()).isEqualTo(3402);
        assertThat(service.assign(position("Pressholzplatte quadratisch", "19"), Direction.EINGANG).sachkonto())
                .isEqualTo(3403);
    }

    @Test
    void outgoingInvoicesAreSplitOnlyByTaxRate() {
        assertThat(service.assign(position("Spüle 70cm", "19"), Direction.AUSGANG).sachkonto()).isEqualTo(8400);
        assertThat(service.assign(position("Brennholz", "7"), Direction.AUSGANG).sachkonto()).isEqualTo(8300);
    }

    @Test
    void automaticAccountsCarryNoTaxKeyWhileManualOnesDo() {
        AccountAssignment erloes = service.assign(position("Spüle 70cm", "19"), Direction.AUSGANG);
        AccountAssignment aufwand = service.assign(position("Furnier Ahorn 80 cm", "19"), Direction.EINGANG);
        AccountAssignment ermaessigt = service.assign(position("Furnier Ahorn 80 cm", "7"), Direction.EINGANG);

        assertThat(erloes.automatic()).isTrue();
        assertThat(erloes.steuerschluessel()).isNull();
        assertThat(aufwand.automatic()).isFalse();
        assertThat(aufwand.steuerschluessel()).isEqualTo(9);
        assertThat(ermaessigt.steuerschluessel()).isEqualTo(8);
    }

    @Test
    void unknownPositionTextsFallBackToTheGeneralGoodsAccountWithLowConfidence() {
        AccountAssignment assignment = service.assign(position("Sonderleistung XY-4711", "19"), Direction.EINGANG);

        assertThat(assignment.sachkonto()).isEqualTo(3400);
        assertThat(assignment.confident()).isFalse();
        assertThat(assignment.reason()).contains("keine Kategorie");
    }

    @Test
    void keepsTheGoodsAccountForSevenPercentPositions() {
        assertThat(service.assign(position("Sonderleistung XY-4711", "7"), Direction.EINGANG).sachkonto())
                .isEqualTo(3300);
    }

    @Test
    void linesWithoutATaxRateStillReceiveAnAccountButAreNotConfident() {
        InvoiceLine ohneSteuer = new InvoiceLine("Furnier Ahorn 80 cm", BigDecimal.ONE,
                new BigDecimal("10.00"), new BigDecimal("10.00"), null);

        AccountAssignment assignment = service.assign(ohneSteuer, Direction.EINGANG);

        assertThat(assignment.sachkonto()).isEqualTo(3401);
        assertThat(assignment.confident()).isFalse();
    }
}
