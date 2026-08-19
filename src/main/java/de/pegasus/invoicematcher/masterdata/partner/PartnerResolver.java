package de.pegasus.invoicematcher.masterdata.partner;

import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.Party;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Setzt Punkt 5 des Regelwerks um: Personenkonto aus den Stammdaten, sonst Diverse-Konto.
 *
 * <p>Die Suchreihenfolge folgt der Belastbarkeit der Merkmale - erst die steuerlichen
 * Identifikatoren, dann der Name. Ein unbekannter Partner ist ausdruecklich kein Abbruch.
 */
@Component
public class PartnerResolver {

    /** Ab dieser Aehnlichkeit gilt ein Name als derselbe Partner. */
    private static final double NAME_MATCH_THRESHOLD = 0.90;

    private final PartnerRepository repository;
    private final DiverseAccountRange diverseAccounts;

    public PartnerResolver(PartnerRepository repository, DiverseAccountRange diverseAccounts) {
        this.repository = repository;
        this.diverseAccounts = diverseAccounts;
    }

    public PartnerMatch resolve(Party party, Direction direction) {
        List<BusinessPartner> candidates = repository.findAll(direction);
        return byTaxIdentifier(party, candidates)
                .map(partner -> new PartnerMatch(partner, PartnerMatch.Quality.STEUERNUMMER))
                .or(() -> byName(party, candidates).map(partner -> new PartnerMatch(partner, PartnerMatch.Quality.NAME)))
                .orElseGet(() -> new PartnerMatch(
                        diverseAccounts.accountFor(party.name(), direction), PartnerMatch.Quality.DIVERSE));
    }

    private Optional<BusinessPartner> byTaxIdentifier(Party party, List<BusinessPartner> candidates) {
        return candidates.stream()
                .filter(partner -> matches(party.vatId(), partner.vatId())
                        || matches(party.taxNumber(), partner.taxNumber()))
                .findFirst();
    }

    private Optional<BusinessPartner> byName(Party party, List<BusinessPartner> candidates) {
        if (!party.hasName()) {
            return Optional.empty();
        }
        return candidates.stream()
                .map(partner -> new Scored(partner, PartyNames.similarity(party.name(), partner.name())))
                .filter(scored -> scored.score() >= NAME_MATCH_THRESHOLD)
                .max(Comparator.comparingDouble(Scored::score))
                .map(Scored::partner);
    }

    /** Steuerliche Nummern werden in Belegen mit wechselnden Leerzeichen gedruckt. */
    private static boolean matches(String fromDocument, String fromMasterData) {
        if (fromDocument == null || fromMasterData == null) {
            return false;
        }
        return normalize(fromDocument).equals(normalize(fromMasterData));
    }

    private static String normalize(String identifier) {
        return identifier.replaceAll("[\\s.]", "").toUpperCase(java.util.Locale.ROOT);
    }

    private record Scored(BusinessPartner partner, double score) {
    }
}
