package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.persistence.SettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Die Belegannahme-per-Mail-Einstellungen, live aus der Einstellungen-Ablage statt einmalig
 * beim Start gebunden. Der An/Aus-Zustand, die Zugangsdaten und der Takt aendern sich jetzt
 * ohne Neustart - {@link de.pegasus.invoicematcher.intake.mail.MailIntakeScheduler} liest sie
 * bei jedem Takt frisch.
 */
@Service
public class MailIntakeSettingsService implements Supplier<MailIntakeProperties> {

    private static final Logger log = LoggerFactory.getLogger(MailIntakeSettingsService.class);

    private final SettingsRepository repository;
    private final MailIntakeProperties vorgabewerte;

    /** {@code vorgabewerte}: siehe {@link CompanySettingsService#CompanySettingsService}. */
    public MailIntakeSettingsService(SettingsRepository repository, MailIntakeProperties vorgabewerte) {
        this.repository = repository;
        this.vorgabewerte = vorgabewerte;
    }

    public MailIntakeProperties current() {
        return repository.loadMailIntake().orElse(vorgabewerte);
    }

    @Override
    public MailIntakeProperties get() {
        return current();
    }

    /**
     * Ein leeres Passwort in {@code neu} heisst "unveraendert lassen", nicht "loeschen" - die
     * Oberflaeche zeigt das vorhandene Passwort aus Sicherheitsgruenden nie an, ein Formular
     * ohne Eingabe darf es deshalb nicht versehentlich wegnehmen.
     */
    public MailIntakeProperties update(MailIntakeProperties neu) {
        if (neu.enabled() && (neu.host() == null || neu.host().isBlank())) {
            throw new IllegalArgumentException("Der IMAP-Server darf nicht leer sein, wenn die Annahme aktiv ist");
        }
        if (neu.enabled() && (neu.username() == null || neu.username().isBlank())) {
            throw new IllegalArgumentException("Der Postfach-Benutzername darf nicht leer sein,"
                    + " wenn die Annahme aktiv ist");
        }
        if (neu.port() == null || neu.port() < 1 || neu.port() > 65535) {
            throw new IllegalArgumentException("Der Port muss zwischen 1 und 65535 liegen");
        }
        String passwort = neu.password() == null || neu.password().isBlank()
                ? current().password()
                : neu.password();
        MailIntakeProperties gespeichert = new MailIntakeProperties(neu.enabled(), neu.protocol(), neu.host(),
                neu.port(), neu.username(), passwort, neu.folder(), neu.processedFolder(), neu.failedFolder(),
                neu.pollInterval(), neu.maxMessagesPerPoll(), neu.maxAttachmentSize(), neu.allowedSenders());
        if (gespeichert.enabled() && gespeichert.allowedSenders().isEmpty()) {
            log.warn("Fuer die Belegannahme per Mail ist keine Absenderliste hinterlegt -"
                    + " jeder Absender kann Belege in die Buchhaltung einliefern");
        }
        repository.saveMailIntake(gespeichert);
        return gespeichert;
    }
}
