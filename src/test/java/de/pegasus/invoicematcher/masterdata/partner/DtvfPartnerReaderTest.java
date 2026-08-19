package de.pegasus.invoicematcher.masterdata.partner;

import de.pegasus.invoicematcher.domain.Direction;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DtvfPartnerReaderTest {

    /** DATEV exportiert Debitoren/Kreditoren in Windows-1252 mit Semikolon als Trenner. */
    private static final String EXPORT = """
            Konto;Name (Adressattyp Unternehmen);IBAN;Ust-ID;Steuernummer
            10001;Diverse A;;;
            10101;Erwin Laubangermuster;;;
            70101;Friedrich Klausmuster;DE86500104240000000100;DE 404131066;156/290/44989
            70102;Sägewerk Kumpfmühle Test;DE88 7605 0000;DE 815000093;241/116/31421
            """;

    private final DtvfPartnerReader reader = new DtvfPartnerReader();

    @Test
    void readsCreditorsAndDebitorsWithTheirTaxIdentifiers() {
        List<BusinessPartner> partners = reader.read(EXPORT.getBytes(Charset.forName("windows-1252")));

        assertThat(partners).hasSize(4);
        assertThat(partners).anySatisfy(partner -> {
            assertThat(partner.konto()).isEqualTo(70102);
            assertThat(partner.name()).isEqualTo("Sägewerk Kumpfmühle Test");
            assertThat(partner.direction()).isEqualTo(Direction.EINGANG);
            assertThat(partner.vatId()).isEqualTo("DE 815000093");
            assertThat(partner.taxNumber()).isEqualTo("241/116/31421");
        });
    }

    @Test
    void derivesDirectionFromTheDatevAccountRange() {
        List<BusinessPartner> partners = reader.read(EXPORT.getBytes(Charset.forName("windows-1252")));

        assertThat(partners).filteredOn(partner -> partner.konto() < 70000)
                .allSatisfy(partner -> assertThat(partner.direction()).isEqualTo(Direction.AUSGANG));
    }

    @Test
    void skipsRowsWithoutAUsableAccountNumber() {
        List<BusinessPartner> partners = reader.read(
                "Konto;Name\n;Ohne Konto\nabc;Kein Konto\n70101;Friedrich Klausmuster\n"
                        .getBytes(Charset.forName("windows-1252")));

        assertThat(partners).singleElement().extracting(BusinessPartner::konto).isEqualTo(70101);
    }
}
