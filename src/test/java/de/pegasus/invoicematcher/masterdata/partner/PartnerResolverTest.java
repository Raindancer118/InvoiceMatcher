package de.pegasus.invoicematcher.masterdata.partner;

import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.Party;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerResolverTest {

    private static final List<BusinessPartner> STAMMDATEN = List.of(
            new BusinessPartner(70101, "Friedrich Klausmuster", Direction.EINGANG, "DE 404131066", "156/290/44989"),
            new BusinessPartner(70102, "Sägewerk Kumpfmühle Test", Direction.EINGANG, "DE 815000093", "241/116/31421"),
            new BusinessPartner(70108, "Unkastester & Hübnerbeispiel", Direction.EINGANG, "DE 137416438", "181/815/57016"),
            new BusinessPartner(10101, "Erwin Laubangermuster", Direction.AUSGANG, null, null));

    private final PartnerResolver resolver =
            new PartnerResolver(new InMemoryPartnerRepository(STAMMDATEN), new DiverseAccountRange(70001, 10001));

    @Test
    void prefersTheVatIdBecauseItIsUnambiguous() {
        PartnerMatch match = resolver.resolve(new Party("Klausmuster GmbH & Co", "DE 404131066", null), Direction.EINGANG);

        assertThat(match.partner().konto()).isEqualTo(70101);
        assertThat(match.quality()).isEqualTo(PartnerMatch.Quality.STEUERNUMMER);
        assertThat(match.isKnown()).isTrue();
    }

    @Test
    void fallsBackToTheTaxNumberWhenNoVatIdIsPrinted() {
        PartnerMatch match = resolver.resolve(new Party("unleserlich", null, "241/116/31421"), Direction.EINGANG);

        assertThat(match.partner().konto()).isEqualTo(70102);
        assertThat(match.quality()).isEqualTo(PartnerMatch.Quality.STEUERNUMMER);
    }

    @Test
    void matchesByNameIgnoringCaseUmlautsAndLegalForm() {
        assertThat(resolver.resolve(new Party("Saegewerk Kumpfmuehle Test GmbH", null, null), Direction.EINGANG))
                .extracting(match -> match.partner().konto()).isEqualTo(70102);
        assertThat(resolver.resolve(new Party("unkastester & huebnerbeispiel", null, null), Direction.EINGANG))
                .extracting(match -> match.partner().konto()).isEqualTo(70108);
    }

    @Test
    void toleratesSmallSpellingDeviations() {
        PartnerMatch match = resolver.resolve(new Party("Friedrich Klausmusler", null, null), Direction.EINGANG);

        assertThat(match.partner().konto()).isEqualTo(70101);
        assertThat(match.quality()).isEqualTo(PartnerMatch.Quality.NAME);
    }

    @Test
    void doesNotConfuseCreditorsWithDebitors() {
        PartnerMatch match = resolver.resolve(new Party("Friedrich Klausmuster", "DE 404131066", null), Direction.AUSGANG);

        assertThat(match.isKnown()).isFalse();
        assertThat(match.partner().konto()).isEqualTo(10006);
    }

    @Test
    void assignsDiverseAccountByFirstLetterWhenThePartnerIsUnknown() {
        PartnerMatch match = resolver.resolve(new Party("Dietrich Tonexempel", "DE 801763659", null), Direction.EINGANG);

        assertThat(match.isKnown()).isFalse();
        assertThat(match.quality()).isEqualTo(PartnerMatch.Quality.DIVERSE);
        assertThat(match.partner().konto()).isEqualTo(70004);
        assertThat(match.partner().name()).isEqualTo("Diverse D");
    }

    @Test
    void resolvesUmlautsAndSkipsLeadingNonLettersWhenChoosingTheDiverseAccount() {
        // "Ö" faellt auf "O" zurueck, damit Schreibweisen mit und ohne Umlaut zusammenfinden
        assertThat(resolver.resolve(new Party("Ölmühle Nord", null, null), Direction.EINGANG).partner().konto())
                .isEqualTo(70015);
        // fuehrende Ziffern werden uebersprungen: "3M" wird unter "M" gefuehrt
        assertThat(resolver.resolve(new Party("3M Deutschland", null, null), Direction.AUSGANG).partner().konto())
                .isEqualTo(10013);
    }

    @Test
    void usesTheAlphabeticallyFirstDiverseAccountWhenNoNameWasRecognised() {
        PartnerMatch match = resolver.resolve(Party.UNKNOWN, Direction.EINGANG);

        assertThat(match.partner().konto()).isEqualTo(70001);
        assertThat(match.quality()).isEqualTo(PartnerMatch.Quality.DIVERSE);
    }
}
