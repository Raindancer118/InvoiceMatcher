package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.persistence.SettingsRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.Supplier;

/**
 * Die Freigabeschwelle des Human-in-the-Loop, live aus der Einstellungen-Ablage.
 *
 * <p>Wird bei jedem Beleg frisch gelesen (siehe {@code ReviewService}): eine verschobene
 * Schwelle wirkt sofort, ohne Neustart - wie Kontenrahmen, Partnerliste und Modellwahl.
 *
 * <p>Die Benachrichtigungseinstellungen bleiben bewusst bei den Umgebungswerten: ueber sie
 * entscheidet beim Start, ob es einen Mail-Versender ueberhaupt gibt
 * ({@code MailReviewNotifier} haengt an {@code @ConditionalOnProperty}), und das laesst sich
 * zur Laufzeit nicht ehrlich umschalten.
 */
@Service
public class ReviewSettingsService implements Supplier<ReviewProperties> {

    private final SettingsRepository repository;
    private final ReviewProperties vorgabewerte;

    public ReviewSettingsService(SettingsRepository repository, ReviewProperties vorgabewerte) {
        this.repository = repository;
        this.vorgabewerte = vorgabewerte;
    }

    public ReviewProperties current() {
        return new ReviewProperties(
                repository.loadReviewThreshold().orElse(vorgabewerte.autoApproveThreshold()),
                vorgabewerte.notification());
    }

    @Override
    public ReviewProperties get() {
        return current();
    }

    /**
     * @param autoApproveThreshold Konfidenz zwischen 0 und 1; 1 heisst "nichts laeuft automatisch
     *                             durch", 0 "alles, was die KI buchen will, wird gebucht"
     */
    public ReviewProperties update(BigDecimal autoApproveThreshold) {
        if (autoApproveThreshold == null) {
            throw new IllegalArgumentException("Die Freigabeschwelle darf nicht leer sein");
        }
        if (autoApproveThreshold.compareTo(BigDecimal.ZERO) < 0
                || autoApproveThreshold.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Die Freigabeschwelle muss zwischen 0 und 1 liegen");
        }
        repository.saveReview(autoApproveThreshold);
        return current();
    }
}
