package de.pegasus.invoicematcher.intake.mail;

import de.pegasus.invoicematcher.config.MailIntakeConfiguration;
import de.pegasus.invoicematcher.config.MailIntakeProperties;
import de.pegasus.invoicematcher.review.ReviewService;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Verdrahtung der Belegannahme per Mail - Schalter und Bindung der Einstellungen.
 *
 * <p>Beides faellt sonst erst im Betrieb auf: ein falsch geschriebener Zeitraum oder eine
 * Groessenangabe bricht den Anwendungsstart, und zwar auf dem Server, nicht hier.
 */
class MailIntakeConfigurationTest {

    @Test
    void withoutTheSwitchNothingIsWiredUp() {
        new ApplicationContextRunner()
                .withUserConfiguration(MailIntakeConfiguration.class)
                .withBean(ReviewService.class, () -> Kontierung.standardWorkflow().review())
                .run(context -> assertThat(context).doesNotHaveBean(MailIntakeService.class));
    }

    @Test
    void theConfiguredMailboxIsBoundIncludingDurationsAndSizes() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(MailIntakeConfiguration.class, PropertiesHolder.class)
                .withBean(ReviewService.class, () -> Kontierung.standardWorkflow().review())
                .withPropertyValues(
                        "invoicematcher.intake.mail.enabled=true",
                        "invoicematcher.intake.mail.host=mx.example",
                        "invoicematcher.intake.mail.username=belege@example.de",
                        "invoicematcher.intake.mail.password=geheim",
                        "invoicematcher.intake.mail.poll-interval=10m",
                        "invoicematcher.intake.mail.max-attachment-size=5MB",
                        "invoicematcher.intake.mail.allowed-senders[0]=@example.de")
                .run(context -> {
                    assertThat(context).hasSingleBean(MailIntakeService.class);
                    assertThat(context).hasSingleBean(MailIntakeScheduler.class);
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
                .withUserConfiguration(MailIntakeConfiguration.class, PropertiesHolder.class)
                .withBean(ReviewService.class, () -> Kontierung.standardWorkflow().review())
                .withPropertyValues("invoicematcher.intake.mail.enabled=true",
                        "invoicematcher.intake.mail.host=mx.example")
                .withPropertyValues(properties);
    }

    /** Nur damit die Einstellungen im Testkontext gebunden werden. */
    @org.springframework.boot.context.properties.EnableConfigurationProperties(MailIntakeProperties.class)
    static class PropertiesHolder {
    }
}
