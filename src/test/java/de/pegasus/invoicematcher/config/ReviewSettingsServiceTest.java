package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.persistence.SettingsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Die Freigabeschwelle ist der empfindlichste Wert der Anwendung: zu hoch gesetzt wandert jeder
 * Beleg in die Pruefung, zu niedrig wird falsch kontiert gebucht. Deshalb gehoert sie in die
 * Einstellungen - und deshalb muss sie gegen Unsinn abgesichert sein.
 */
class ReviewSettingsServiceTest {

    private final ReviewProperties vorgabewerte = new ReviewProperties(new BigDecimal("0.85"),
            new ReviewProperties.Notification(false, "von@example.de", "an@example.de"));

    private ReviewSettingsService service(BigDecimal gespeichert) {
        return new ReviewSettingsService(new SettingsRepository(null, null) {
            private BigDecimal wert = gespeichert;

            @Override
            public Optional<BigDecimal> loadReviewThreshold() {
                return Optional.ofNullable(wert);
            }

            @Override
            public void saveReview(BigDecimal schwelle) {
                wert = schwelle;
            }
        }, vorgabewerte);
    }

    @Test
    void fallsBackToTheConfiguredDefaultUntilSomethingWasSaved() {
        assertThat(service(null).current().autoApproveThreshold()).isEqualByComparingTo("0.85");
    }

    @Test
    void prefersTheStoredThresholdOverTheDefault() {
        assertThat(service(new BigDecimal("0.60")).current().autoApproveThreshold())
                .isEqualByComparingTo("0.60");
    }

    /** Die Benachrichtigung haengt am Bean-Zustand beim Start und bleibt deshalb unangetastet. */
    @Test
    void keepsTheNotificationSettingsFromTheEnvironment() {
        assertThat(service(new BigDecimal("0.60")).current().notification())
                .isEqualTo(vorgabewerte.notification());
    }

    @Test
    void storesANewThresholdAndHandsItOutImmediately() {
        ReviewSettingsService service = service(null);

        service.update(new BigDecimal("0.7"));

        assertThat(service.current().autoApproveThreshold()).isEqualByComparingTo("0.7");
    }

    @Test
    void refusesAThresholdOutsideOfTheConfidenceRange() {
        ReviewSettingsService service = service(null);

        assertThatThrownBy(() -> service.update(new BigDecimal("1.5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zwischen 0 und 1");
        assertThatThrownBy(() -> service.update(new BigDecimal("-0.1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
