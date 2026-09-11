package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.persistence.SettingsRepository;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Modellwahl und Schluessel-Pools der KI-Klassifikation, live aus der Einstellungen-Ablage.
 *
 * <p>Wird bei jeder Klassifikation frisch gelesen (siehe {@code TieredInvoiceClassifier}): ein
 * neuer Schluessel oder ein anderes Modell wirkt sofort, ohne Neustart.
 */
@Service
public class AiSettingsService implements Supplier<AiProperties> {

    private final SettingsRepository repository;
    private final AiProperties vorgabewerte;

    public AiSettingsService(SettingsRepository repository, AiProperties vorgabewerte) {
        this.repository = repository;
        this.vorgabewerte = vorgabewerte;
    }

    public AiProperties current() {
        return repository.loadAi().orElse(vorgabewerte);
    }

    @Override
    public AiProperties get() {
        return current();
    }

    /**
     * Ein leeres Schluessel-Feld heisst "unveraendert lassen", nicht "loeschen" - wie beim
     * Mail-Passwort ({@link MailIntakeSettingsService#update}) zeigt die Oberflaeche
     * gespeicherte Schluessel nie im Klartext an.
     */
    public AiProperties update(AiProperties neu) {
        if (neu.fast().model() == null || neu.fast().model().isBlank()) {
            throw new IllegalArgumentException("Das schnelle Modell darf nicht leer sein");
        }
        if (neu.smart().model() == null || neu.smart().model().isBlank()) {
            throw new IllegalArgumentException("Das starke Modell darf nicht leer sein");
        }
        AiProperties aktuell = current();
        AiProperties gespeichert = new AiProperties(
                new AiProperties.Tier(neu.fast().model(),
                        neu.fast().apiKeys().isEmpty() ? aktuell.fast().apiKeys() : neu.fast().apiKeys()),
                new AiProperties.Tier(neu.smart().model(),
                        neu.smart().apiKeys().isEmpty() ? aktuell.smart().apiKeys() : neu.smart().apiKeys()));
        repository.saveAi(gespeichert);
        return gespeichert;
    }
}
