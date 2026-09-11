package de.pegasus.invoicematcher.persistence;

import com.zaxxer.hikari.HikariDataSource;
import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.config.AiProperties;
import de.pegasus.invoicematcher.config.CompanyProperties;
import de.pegasus.invoicematcher.config.MailIntakeProperties;
import de.pegasus.invoicematcher.config.PartnerProperties;
import de.pegasus.invoicematcher.config.SecretCipher;
import de.pegasus.invoicematcher.config.SettingsKeyProperties;
import de.pegasus.invoicematcher.domain.Direction;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Firmendaten und Mail-Eingang muessen einen Neustart ueberstehen - genau wie die
 * Pruefvorgaenge, siehe {@link SqliteReviewPersistenceTest}. Das Postfach-Passwort darf dabei
 * nie im Klartext in der Datenbank landen.
 */
@DisplayName("Einstellungen-Ablage")
class SettingsRepositoryTest {

    private HikariDataSource dataSource;
    private SettingsRepository repository;

    @TempDir
    Path verzeichnis;

    @BeforeEach
    void datenbankAufbauen() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:sqlite:" + verzeichnis.resolve("einstellungen.db"));
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setMaximumPoolSize(1);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);

        byte[] schluesselBytes = new byte[32];
        new SecureRandom().nextBytes(schluesselBytes);
        SecretCipher cipher = new SecretCipher(
                new SettingsKeyProperties(Base64.getEncoder().encodeToString(schluesselBytes)));
        repository = new SettingsRepository(JdbcClient.create(dataSource), cipher);
    }

    @AfterEach
    void datenbankSchliessen() {
        dataSource.close();
    }

    @Test
    void companySettingsAreEmptyBeforeTheFirstSave() {
        assertThat(repository.loadCompany()).isEmpty();
    }

    @Test
    void savedCompanySettingsReturnExactlyWhatWasSaved() {
        repository.saveCompany(new CompanyProperties("Volantic GmbH", "DE123456789", "24112345678"));

        CompanyProperties geladen = repository.loadCompany().orElseThrow();

        assertThat(geladen.name()).isEqualTo("Volantic GmbH");
        assertThat(geladen.vatId()).isEqualTo("DE123456789");
        assertThat(geladen.taxNumber()).isEqualTo("24112345678");
    }

    @Test
    void savingCompanySettingsTwiceUpdatesInsteadOfDuplicating() {
        repository.saveCompany(new CompanyProperties("Erster Name GmbH", null, null));
        repository.saveCompany(new CompanyProperties("Zweiter Name GmbH", null, null));

        assertThat(repository.loadCompany().orElseThrow().name()).isEqualTo("Zweiter Name GmbH");
    }

    @Test
    void mailIntakeSettingsAreEmptyBeforeTheFirstSave() {
        assertThat(repository.loadMailIntake()).isEmpty();
    }

    @Test
    void theStoredPasswordNeverAppearsAsPlaintextInTheDatabase() {
        repository.saveMailIntake(mailIntake("mein-geheimes-passwort"));

        String rohwert = JdbcClient.create(dataSource)
                .sql("SELECT password_encrypted FROM mail_intake_settings WHERE id = 1")
                .query(String.class)
                .single();

        assertThat(rohwert).isNotNull().doesNotContain("mein-geheimes-passwort");
    }

    @Test
    void savedMailIntakeSettingsReturnExactlyWhatWasSaved() {
        repository.saveMailIntake(mailIntake("mein-geheimes-passwort"));

        MailIntakeProperties geladen = repository.loadMailIntake().orElseThrow();

        assertThat(geladen.enabled()).isTrue();
        assertThat(geladen.host()).isEqualTo("mx.volantic.de");
        assertThat(geladen.port()).isEqualTo(993);
        assertThat(geladen.username()).isEqualTo("belege@volantic.de");
        assertThat(geladen.password()).isEqualTo("mein-geheimes-passwort");
        assertThat(geladen.folder()).isEqualTo("INBOX");
        assertThat(geladen.pollInterval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(geladen.allowedSenders()).containsExactly("a@example.com", "b@example.com");
    }

    @Test
    void aBlankAllowedSendersListRoundTripsAsEmpty() {
        repository.saveMailIntake(new MailIntakeProperties(false, "imaps", "mx.volantic.de", 993,
                "belege@volantic.de", null, "INBOX", "Verarbeitet", "Fehlgeschlagen",
                Duration.ofMinutes(5), 50, DataSize.ofMegabytes(20), List.of()));

        assertThat(repository.loadMailIntake().orElseThrow().allowedSenders()).isEmpty();
    }

    @Test
    void aMissingPasswordRoundTripsAsNull() {
        repository.saveMailIntake(new MailIntakeProperties(false, "imaps", "mx.volantic.de", 993,
                "belege@volantic.de", null, "INBOX", "Verarbeitet", "Fehlgeschlagen",
                Duration.ofMinutes(5), 50, DataSize.ofMegabytes(20), List.of()));

        assertThat(repository.loadMailIntake().orElseThrow().password()).isNull();
    }

    @Test
    void accountingSettingsAreEmptyBeforeTheFirstSave() {
        assertThat(repository.loadAccounting()).isEmpty();
    }

    @Test
    void savedAccountingSettingsReturnExactlyWhatWasSaved() {
        AccountingProperties accounting = new AccountingProperties(new BigDecimal("800.00"),
                new BigDecimal("0.01"),
                List.of(new AccountingProperties.VatAccount(new BigDecimal("19"), 8400, true)),
                List.of(new AccountingProperties.VatAccount(new BigDecimal("19"), 3400, true)),
                List.of(new AccountingProperties.ExpenseCategory("Furniere/Kanten", 3401, false,
                        List.of("furnier"))),
                List.of("maschine"));

        repository.saveAccounting(accounting);

        AccountingProperties geladen = repository.loadAccounting().orElseThrow();
        assertThat(geladen.gwgLimit()).isEqualByComparingTo("800.00");
        assertThat(geladen.categories()).singleElement()
                .satisfies(c -> assertThat(c.label()).isEqualTo("Furniere/Kanten"));
    }

    @Test
    void savingAccountingSettingsTwiceUpdatesInsteadOfDuplicating() {
        repository.saveAccounting(new AccountingProperties(new BigDecimal("800.00"), new BigDecimal("0.01"),
                List.of(), List.of(), List.of(), List.of()));
        repository.saveAccounting(new AccountingProperties(new BigDecimal("1200.00"), new BigDecimal("0.01"),
                List.of(), List.of(), List.of(), List.of()));

        assertThat(repository.loadAccounting().orElseThrow().gwgLimit()).isEqualByComparingTo("1200.00");
    }

    @Test
    void partnerSettingsAreEmptyBeforeTheFirstSave() {
        assertThat(repository.loadPartners()).isEmpty();
    }

    @Test
    void savedPartnerSettingsReturnExactlyWhatWasSaved() {
        PartnerProperties partners = new PartnerProperties(new PartnerProperties.Diverse(70001, 10001),
                List.of(new PartnerProperties.KnownPartner(70101, "Friedrich Klausmuster", Direction.EINGANG,
                        "DE404131066", null)));

        repository.savePartners(partners);

        PartnerProperties geladen = repository.loadPartners().orElseThrow();
        assertThat(geladen.diverse().creditorStart()).isEqualTo(70001);
        assertThat(geladen.known()).singleElement()
                .satisfies(p -> assertThat(p.name()).isEqualTo("Friedrich Klausmuster"));
    }

    @Test
    void aiSettingsAreEmptyBeforeTheFirstSave() {
        assertThat(repository.loadAi()).isEmpty();
    }

    @Test
    void theStoredAiKeysNeverAppearAsPlaintextInTheDatabase() {
        repository.saveAi(new AiProperties(
                new AiProperties.Tier("llama-3.3-70b-versatile", List.of("geheimer-groq-schluessel")),
                new AiProperties.Tier("gemini-2.5-pro", List.of("geheimer-gemini-schluessel"))));

        String rohwert = JdbcClient.create(dataSource)
                .sql("SELECT fast_api_keys_encrypted FROM ai_settings WHERE id = 1")
                .query(String.class)
                .single();

        assertThat(rohwert).isNotNull().doesNotContain("geheimer-groq-schluessel");
    }

    @Test
    void savedAiSettingsReturnExactlyWhatWasSaved() {
        repository.saveAi(new AiProperties(
                new AiProperties.Tier("llama-3.3-70b-versatile", List.of("groq-key-1", "groq-key-2")),
                new AiProperties.Tier("gemini-2.5-pro", List.of("gemini-key-1"))));

        AiProperties geladen = repository.loadAi().orElseThrow();
        assertThat(geladen.fast().model()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(geladen.fast().apiKeys()).containsExactly("groq-key-1", "groq-key-2");
        assertThat(geladen.smart().apiKeys()).containsExactly("gemini-key-1");
    }

    @Test
    void aMissingAiKeyListRoundTripsAsEmpty() {
        repository.saveAi(new AiProperties(new AiProperties.Tier("llama-3.3-70b-versatile", List.of()),
                new AiProperties.Tier("gemini-2.5-pro", List.of())));

        assertThat(repository.loadAi().orElseThrow().fast().apiKeys()).isEmpty();
    }

    private static MailIntakeProperties mailIntake(String passwort) {
        return new MailIntakeProperties(true, "imaps", "mx.volantic.de", 993, "belege@volantic.de", passwort,
                "INBOX", "Verarbeitet", "Fehlgeschlagen", Duration.ofMinutes(5), 50, DataSize.ofMegabytes(20),
                List.of("a@example.com", "b@example.com"));
    }
}
