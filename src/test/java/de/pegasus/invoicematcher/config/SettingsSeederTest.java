package de.pegasus.invoicematcher.config;

import com.zaxxer.hikari.HikariDataSource;
import de.pegasus.invoicematcher.persistence.SettingsRepository;
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
 * Der Seeder darf eine ueber die Oberflaeche geaenderte Einstellung nie wieder ueberschreiben -
 * sonst waere jede Aenderung nur bis zum naechsten Neustart gueltig.
 */
@DisplayName("SettingsSeeder")
class SettingsSeederTest {

    private static final CompanyProperties VORGABE_FIRMA =
            new CompanyProperties("Muster zu Testzwecken GmbH", "DE 815000010", "24112345678");
    private static final MailIntakeProperties VORGABE_MAIL = new MailIntakeProperties(false, "imaps",
            "mx.volantic.de", 993, "belege@volantic.de", null, "INBOX", "Verarbeitet", "Fehlgeschlagen",
            Duration.ofMinutes(5), 50, DataSize.ofMegabytes(20), List.of());

    private HikariDataSource dataSource;
    private SettingsRepository repository;

    @TempDir
    Path verzeichnis;

    @BeforeEach
    void datenbankAufbauen() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:sqlite:" + verzeichnis.resolve("seeder.db"));
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
    void seedsBothTablesFromTheDefaultsWhenEmpty() {
        new SettingsSeeder(repository, VORGABE_FIRMA, VORGABE_MAIL).run(null);

        assertThat(repository.loadCompany().orElseThrow().name()).isEqualTo("Muster zu Testzwecken GmbH");
        assertThat(repository.loadMailIntake().orElseThrow().host()).isEqualTo("mx.volantic.de");
    }

    @Test
    void leavesAnAlreadyChangedCompanySettingUntouched() {
        repository.saveCompany(new CompanyProperties("Echtes Unternehmen GmbH", "DE999999999", "1"));

        new SettingsSeeder(repository, VORGABE_FIRMA, VORGABE_MAIL).run(null);

        assertThat(repository.loadCompany().orElseThrow().name()).isEqualTo("Echtes Unternehmen GmbH");
    }

    @Test
    void leavesAnAlreadyChangedMailIntakeSettingUntouched() {
        repository.saveMailIntake(new MailIntakeProperties(true, "imaps", "echter-server.example", 993,
                "belege@echt.example", null, "INBOX", "Verarbeitet", "Fehlgeschlagen", Duration.ofMinutes(5), 50,
                DataSize.ofMegabytes(20), List.of()));

        new SettingsSeeder(repository, VORGABE_FIRMA, VORGABE_MAIL).run(null);

        assertThat(repository.loadMailIntake().orElseThrow().host()).isEqualTo("echter-server.example");
    }
}
