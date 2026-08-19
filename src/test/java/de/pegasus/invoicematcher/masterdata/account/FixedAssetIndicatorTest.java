package de.pegasus.invoicematcher.masterdata.account;

import de.pegasus.invoicematcher.domain.InvoiceLine;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FixedAssetIndicatorTest {

    private final FixedAssetIndicator indicator = Kontierung.standardFixedAssetIndicator();

    private static InvoiceLine position(String description, String unitPrice) {
        return new InvoiceLine(description, BigDecimal.ONE, new BigDecimal(unitPrice),
                new BigDecimal(unitPrice), new BigDecimal("19"));
    }

    @Test
    void flagsEquipmentAboveTheGwgThreshold() {
        assertThat(indicator.exceedsGwgThreshold(position("Digitalkopiergerät HS-1233", "899.99"))).isTrue();
    }

    @Test
    void ignoresEquipmentBelowTheThresholdBecauseItIsAGwg() {
        assertThat(indicator.exceedsGwgThreshold(position("Digitalkopiergerät HS-1233", "799.00"))).isFalse();
    }

    @Test
    void ignoresExpensiveConsumablesBecauseTheyAreNoFixedAsset() {
        assertThat(indicator.exceedsGwgThreshold(position("Furnier Kirschbaum 50 cm", "1200.00"))).isFalse();
        assertThat(indicator.exceedsGwgThreshold(position("Arbeitsplatte Eiche 80 cm", "950.00"))).isFalse();
    }

    @Test
    void derivesTheUnitPriceFromLineTotalAndQuantityWhenNotPrinted() {
        InvoiceLine line = new InvoiceLine("Werkzeugmaschine", new BigDecimal("2"),
                null, new BigDecimal("2400.00"), new BigDecimal("19"));

        assertThat(indicator.exceedsGwgThreshold(line)).isTrue();
    }

    @Test
    void staysSilentWhenNoPriceInformationIsAvailable() {
        assertThat(indicator.exceedsGwgThreshold(
                new InvoiceLine("Maschine", null, null, null, null))).isFalse();
    }
}
