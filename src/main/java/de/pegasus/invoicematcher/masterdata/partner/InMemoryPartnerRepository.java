package de.pegasus.invoicematcher.masterdata.partner;

import de.pegasus.invoicematcher.domain.Direction;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Haelt die Stammdaten im Speicher - passend fuer den Prototyp und fuer Tests. */
public class InMemoryPartnerRepository implements PartnerRepository {

    private final Map<Direction, List<BusinessPartner>> byDirection;

    public InMemoryPartnerRepository(List<BusinessPartner> partners) {
        this.byDirection = partners.stream().collect(Collectors.groupingBy(BusinessPartner::direction));
    }

    @Override
    public List<BusinessPartner> findAll(Direction direction) {
        return byDirection.getOrDefault(direction, List.of());
    }
}
