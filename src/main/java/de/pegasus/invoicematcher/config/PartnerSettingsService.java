package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.persistence.SettingsRepository;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Die Geschaeftspartnerliste, live aus der Einstellungen-Ablage statt einmalig beim Start
 * gebunden - siehe {@link AccountingSettingsService}, gleiches Prinzip.
 */
@Service
public class PartnerSettingsService implements Supplier<PartnerProperties> {

    private final SettingsRepository repository;
    private final PartnerProperties vorgabewerte;

    public PartnerSettingsService(SettingsRepository repository, PartnerProperties vorgabewerte) {
        this.repository = repository;
        this.vorgabewerte = vorgabewerte;
    }

    public PartnerProperties current() {
        return repository.loadPartners().orElse(vorgabewerte);
    }

    @Override
    public PartnerProperties get() {
        return current();
    }

    public PartnerProperties update(PartnerProperties neu) {
        if (neu == null || neu.diverse() == null) {
            throw new IllegalArgumentException("Die Diverse-Konten-Spanne darf nicht leer sein");
        }
        repository.savePartners(neu);
        return neu;
    }
}
