package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.persistence.SettingsRepository;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Der Kontenrahmen, live aus der Einstellungen-Ablage statt einmalig beim Start gebunden.
 *
 * <p>Die KI-Klassifikation liest ihn bei jeder Anfrage frisch - ein geaenderter Kontenrahmen
 * wirkt sofort auf den naechsten Beleg, kein Neustart noetig.
 */
@Service
public class AccountingSettingsService implements Supplier<AccountingProperties> {

    private final SettingsRepository repository;
    private final AccountingProperties vorgabewerte;

    /** {@code vorgabewerte}: siehe {@link CompanySettingsService#CompanySettingsService}. */
    public AccountingSettingsService(SettingsRepository repository, AccountingProperties vorgabewerte) {
        this.repository = repository;
        this.vorgabewerte = vorgabewerte;
    }

    public AccountingProperties current() {
        return repository.loadAccounting().orElse(vorgabewerte);
    }

    @Override
    public AccountingProperties get() {
        return current();
    }

    public AccountingProperties update(AccountingProperties neu) {
        if (neu == null) {
            throw new IllegalArgumentException("Der Kontenrahmen darf nicht leer sein");
        }
        if (neu.gwgLimit() == null || neu.gwgLimit().signum() < 0) {
            throw new IllegalArgumentException("Die GWG-Grenze muss gesetzt und nicht negativ sein");
        }
        repository.saveAccounting(neu);
        return neu;
    }
}
