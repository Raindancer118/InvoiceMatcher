package de.pegasus.invoicematcher.extraction.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GermanAmountsTest {

    @ParameterizedTest
    @CsvSource({
            "'480,76', 480.76",
            "'1.070,99', 1070.99",
            "'1.234.567,89', 1234567.89",
            "'0,00', 0.00",
            "'-12,50', -12.50"
    })
    void parsesGermanDecimalNotation(String input, BigDecimal expected) {
        assertThat(GermanAmounts.parse(input)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", "19", "1.070"})
    void rejectsInputWithoutTwoDecimalDigits(String input) {
        assertThat(GermanAmounts.parse(input)).isEmpty();
    }

    @Test
    void lastAmountInLineWinsBecauseTotalsSitInTheRightmostColumn() {
        assertThat(GermanAmounts.lastAmountIn("19% Mehrwertsteuer                        76,76 €"))
                .contains(new BigDecimal("76.76"));
        assertThat(GermanAmounts.lastAmountIn("                Nettosumme                 € 899,99"))
                .contains(new BigDecimal("899.99"));
        assertThat(GermanAmounts.lastAmountIn("Mehrwertsteuer 19,00%                    257,83 €"))
                .contains(new BigDecimal("257.83"));
        assertThat(GermanAmounts.lastAmountIn("Gesamtsumme EUR                          375,50 €"))
                .contains(new BigDecimal("375.50"));
    }

    @Test
    void returnsEmptyWhenLineCarriesNoAmount() {
        assertThat(GermanAmounts.lastAmountIn("Pos. Art.-Nr. Artikelbezeichnung Menge Einheit"))
                .isEqualTo(Optional.empty());
    }

    @Test
    void percentageAloneIsNotAnAmount() {
        assertThat(GermanAmounts.lastAmountIn("zzgl. Mehrwertsteuer (19 %)")).isEmpty();
    }

    @Test
    void extractsPercentageRate() {
        assertThat(GermanAmounts.firstPercentageIn("zzgl. Mehrwertsteuer (19 %)   11,12 €"))
                .contains(new BigDecimal("19"));
        assertThat(GermanAmounts.firstPercentageIn("Mehrwertsteuer 19,00%   257,83 €"))
                .contains(new BigDecimal("19.00"));
        assertThat(GermanAmounts.firstPercentageIn("Nettosumme  404,00 €")).isEmpty();
    }
}
