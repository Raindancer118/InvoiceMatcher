package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.masterdata.partner.BusinessPartner;
import de.pegasus.invoicematcher.masterdata.partner.DiverseAccountRange;
import de.pegasus.invoicematcher.masterdata.partner.DtvfPartnerReader;
import de.pegasus.invoicematcher.masterdata.partner.InMemoryPartnerRepository;
import de.pegasus.invoicematcher.masterdata.partner.PartnerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Baut die Stammdatenquellen auf: Konfiguration, optional ergaenzt um einen DTVF-Export. */
@Configuration
public class MasterDataConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MasterDataConfiguration.class);

    @Bean
    public DiverseAccountRange diverseAccountRange(PartnerProperties properties) {
        return properties.diverse() == null ? DiverseAccountRange.standard() : properties.diverse().toRange();
    }

    @Bean
    public PartnerRepository partnerRepository(PartnerProperties properties, DtvfPartnerReader reader) {
        List<BusinessPartner> partners = new ArrayList<>(properties.toBusinessPartners());
        partners.addAll(fromDtvfExport(properties, reader, partners));
        log.info("Geschaeftspartner-Stammdaten geladen: {} Eintraege", partners.size());
        return new InMemoryPartnerRepository(partners);
    }

    /** Bereits konfigurierte Konten gewinnen; der Export ergaenzt nur, was fehlt. */
    private List<BusinessPartner> fromDtvfExport(PartnerProperties properties, DtvfPartnerReader reader,
                                                 List<BusinessPartner> existing) {
        String file = properties.dtvfFile();
        if (file == null || file.isBlank()) {
            return List.of();
        }
        Path path = Path.of(file);
        if (!Files.isReadable(path)) {
            log.warn("DTVF-Export '{}' ist nicht lesbar - es gelten nur die konfigurierten Partner", file);
            return List.of();
        }
        try {
            List<Integer> knownAccounts = existing.stream().map(BusinessPartner::konto).toList();
            return reader.read(Files.readAllBytes(path)).stream()
                    .filter(partner -> !knownAccounts.contains(partner.konto()))
                    .toList();
        } catch (IOException e) {
            log.warn("DTVF-Export '{}' konnte nicht gelesen werden: {}", file, e.getMessage());
            return List.of();
        }
    }
}
