package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.persistence.SettingsRepository;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Die Firmendaten, live aus der Einstellungen-Ablage statt einmalig beim Start gebunden.
 *
 * <p>Implementiert {@link Supplier}, damit sich diese Bean direkt in Konsumenten wie
 * {@link de.pegasus.invoicematcher.rules.impl.DirectionRule} einsetzen laesst, ohne dass die
 * dortige Fachlogik von diesem Dienst als konkretem Typ abhaengen muss.
 */
@Service
public class CompanySettingsService implements Supplier<CompanyProperties> {

    private final SettingsRepository repository;
    private final CompanyProperties vorgabewerte;

    /**
     * {@code vorgabewerte} sind die aus {@code application.yml}/Umgebung gebundenen Werte -
     * greifen nur, falls in der Datenbank noch nichts steht (vor dem ersten Start des
     * {@code SettingsSeeder} oder in Tests ohne Seeder).
     */
    public CompanySettingsService(SettingsRepository repository, CompanyProperties vorgabewerte) {
        this.repository = repository;
        this.vorgabewerte = vorgabewerte;
    }

    public CompanyProperties current() {
        return repository.loadCompany().orElse(vorgabewerte);
    }

    @Override
    public CompanyProperties get() {
        return current();
    }

    public CompanyProperties update(CompanyProperties neu) {
        if (neu.name() == null || neu.name().isBlank()) {
            throw new IllegalArgumentException("Der Firmenname darf nicht leer sein");
        }
        repository.saveCompany(neu);
        return neu;
    }
}
