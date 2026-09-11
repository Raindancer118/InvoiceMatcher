package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.masterdata.partner.BusinessPartner;
import de.pegasus.invoicematcher.masterdata.partner.DiverseAccountRange;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Geschaeftspartner-Stammdaten. Wird der KI-Klassifikation als Kontext-JSON mitgegeben; ein
 * unbekannter Partner bekommt ueber {@link DiverseAccountRange} deterministisch ein Sammelkonto.
 *
 * @param diverse Nummernkreise der 26 Sammelkonten je Belegrichtung
 * @param known   gepflegte Partner
 */
@ConfigurationProperties("invoicematcher.partners")
public record PartnerProperties(Diverse diverse, List<KnownPartner> known) {

    public PartnerProperties {
        diverse = diverse == null ? new Diverse(70001, 10001) : diverse;
        known = known == null ? List.of() : List.copyOf(known);
    }

    public record Diverse(int creditorStart, int debitorStart) {

        public DiverseAccountRange toRange() {
            return new DiverseAccountRange(creditorStart, debitorStart);
        }
    }

    public record KnownPartner(int konto, String name, Direction direction, String vatId, String taxNumber) {

        public BusinessPartner toBusinessPartner() {
            return new BusinessPartner(konto, name, direction, vatId, taxNumber);
        }
    }

    public List<BusinessPartner> toBusinessPartners() {
        return known == null ? List.of() : known.stream().map(KnownPartner::toBusinessPartner).toList();
    }
}
