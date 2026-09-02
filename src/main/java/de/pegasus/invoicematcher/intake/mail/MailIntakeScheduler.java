package de.pegasus.invoicematcher.intake.mail;

import de.pegasus.invoicematcher.config.MailIntakeProperties;
import de.pegasus.invoicematcher.config.MailIntakeSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holt das Postfach ab - im eigenen, an/aus- und takt-veraenderlichen Rhythmus statt im festen
 * Spring-Takt.
 *
 * <p>{@code @Scheduled} bindet seinen Ausdruck einmal beim Start; eine ueber die Einstellungen-
 * Oberflaeche geaenderte Annahme (Ein/Aus, Takt) wuerde er nie wieder sehen. Der Takt hier ist
 * deshalb ein fester, kurzer Rhythmus, der sich selbst drosselt: uebersprungen wird, wenn die
 * Annahme gerade aus ist oder seit dem letzten echten Durchlauf noch nicht der eingestellte
 * Takt vergangen ist.
 *
 * <p>Ein Fehler beendet den Durchlauf, nicht den Dienst: ein Mailserver, der gerade nicht
 * antwortet, darf die Anwendung nicht anhalten - beim naechsten Takt wird es erneut versucht.
 */
public class MailIntakeScheduler {

    private static final Logger log = LoggerFactory.getLogger(MailIntakeScheduler.class);

    /** Wie oft nachgesehen wird, ob der eingestellte Takt inzwischen erreicht ist. */
    private static final long PRUEFTAKT_MS = 30_000;

    private final MailIntakeService intake;
    private final MailIntakeSettingsService settings;
    private final AtomicReference<Instant> letzterDurchlauf = new AtomicReference<>(Instant.EPOCH);

    public MailIntakeScheduler(MailIntakeService intake, MailIntakeSettingsService settings) {
        this.intake = intake;
        this.settings = settings;
    }

    @Scheduled(fixedDelay = PRUEFTAKT_MS, initialDelay = PRUEFTAKT_MS)
    public void poll() {
        MailIntakeProperties aktuell = settings.current();
        if (!aktuell.enabled()) {
            return;
        }
        Instant jetzt = Instant.now();
        if (Duration.between(letzterDurchlauf.get(), jetzt).compareTo(aktuell.pollInterval()) < 0) {
            return;
        }
        letzterDurchlauf.set(jetzt);
        try {
            int bearbeitet = intake.poll();
            if (bearbeitet > 0) {
                log.info("Belegannahme per Mail: {} Nachricht(en) bearbeitet", bearbeitet);
            }
        } catch (RuntimeException e) {
            log.error("Belegannahme per Mail fehlgeschlagen: {}", e.getMessage());
        }
    }
}
