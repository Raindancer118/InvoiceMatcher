package de.pegasus.invoicematcher.intake.mail;

import de.pegasus.invoicematcher.config.MailIntakeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Bindung von {@link MailIntakeProperties} aus der Umgebung - der Vorgabewert fuer den
 * allerersten Start (siehe {@link de.pegasus.invoicematcher.config.SettingsSeeder}).
 *
 * <p>Ein falsch geschriebener Zeitraum oder eine Groessenangabe faellt sonst erst im Betrieb
 * auf, und zwar auf dem Server, nicht hier. Die Beans der Belegannahme selbst haengen seit der
 * Einstellungen-Oberflaeche nicht mehr an einem Schalter in dieser Konfiguration - sie existieren
 * immer, aktiv ist die Annahme ueber {@link de.pegasus.invoicematcher.config.MailIntakeSettingsService}.
 */
class MailIntakeConfigurationTest {

    @Test
    void theConfiguredPropertiesBindIncludingDurationsAndSizes() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(PropertiesHolder.class)
                .withPropertyValues(
                        "invoicematcher.intake.mail.enabled=true",
                        "invoicematcher.intake.mail.host=mx.example",
                        "invoicematcher.intake.mail.username=belege@example.de",
                        "invoicematcher.intake.mail.password=geheim",
                        "invoicematcher.intake.mail.poll-interval=10m",
                        "invoicematcher.intake.mail.max-attachment-size=5MB",
                        "invoicematcher.intake.mail.allowed-senders[0]=@example.de")
                .run(context -> {
                    MailIntakeProperties properties = context.getBean(MailIntakeProperties.class);
                    assertThat(properties.protocol()).isEqualTo("imaps");
                    assertThat(properties.port()).isEqualTo(993);
                    assertThat(properties.pollInterval()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(properties.maxAttachmentSize().toMegabytes()).isEqualTo(5);
                    assertThat(properties.accepts("buchhaltung@example.de")).isTrue();
                    assertThat(properties.accepts("fremder@spam.example")).isFalse();
                });
    }

    /**
     * Die Einstellung kommt im Betrieb aus einer Umgebungsvariablen und damit als eine
     * Zeichenkette - eine leere Liste muss ebenso ankommen wie mehrere Absender.
     */
    @Test
    void allowedSendersAlsoBindFromACommaSeparatedEnvironmentVariable() {
        binding("invoicematcher.intake.mail.allowed-senders=@example.de,buchhaltung@partner.example")
                .run(context -> {
                    MailIntakeProperties properties = context.getBean(MailIntakeProperties.class);
                    assertThat(properties.allowedSenders()).hasSize(2);
                    assertThat(properties.accepts("buchhaltung@partner.example")).isTrue();
                });

        binding("invoicematcher.intake.mail.allowed-senders=")
                .run(context -> assertThat(context.getBean(MailIntakeProperties.class).allowedSenders())
                        .isEmpty());
    }

    private ApplicationContextRunner binding(String... properties) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(PropertiesHolder.class)
                .withPropertyValues("invoicematcher.intake.mail.enabled=true",
                        "invoicematcher.intake.mail.host=mx.example")
                .withPropertyValues(properties);
    }

    /** Nur damit die Einstellungen im Testkontext gebunden werden. */
    @org.springframework.boot.context.properties.EnableConfigurationProperties(MailIntakeProperties.class)
    static class PropertiesHolder {
    }
}
