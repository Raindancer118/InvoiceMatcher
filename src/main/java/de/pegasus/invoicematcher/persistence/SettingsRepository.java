package de.pegasus.invoicematcher.persistence;

import de.pegasus.invoicematcher.config.CompanyProperties;
import de.pegasus.invoicematcher.config.MailIntakeProperties;
import de.pegasus.invoicematcher.config.SecretCipher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.util.unit.DataSize;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Die ueber die Einstellungen-Oberflaeche aenderbaren Werte in der SQLite-Ablage - genau eine
 * Zeile je Bereich (Firma, Mail-Eingang). Verschluesselt/entschluesselt das Postfach-Passwort
 * ueber {@link SecretCipher}, wie {@link ReportSerializer} das Klassifikationsergebnis fuer die
 * Ablage aufbereitet: Aufrufer sehen nie den Ciphertext, nur die fertige {@link MailIntakeProperties}.
 */
@Repository
public class SettingsRepository {

    private final JdbcClient jdbc;
    private final SecretCipher cipher;

    public SettingsRepository(JdbcClient jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    public Optional<CompanyProperties> loadCompany() {
        return jdbc.sql("SELECT name, vat_id, tax_number FROM company_settings WHERE id = 1")
                .query((row, i) -> new CompanyProperties(row.getString("name"), row.getString("vat_id"),
                        row.getString("tax_number")))
                .optional();
    }

    public void saveCompany(CompanyProperties company) {
        jdbc.sql("""
                INSERT INTO company_settings (id, name, vat_id, tax_number)
                VALUES (1, :name, :vatId, :taxNumber)
                ON CONFLICT (id) DO UPDATE SET
                    name       = excluded.name,
                    vat_id     = excluded.vat_id,
                    tax_number = excluded.tax_number
                """)
                .param("name", company.name())
                .param("vatId", company.vatId())
                .param("taxNumber", company.taxNumber())
                .update();
    }

    public Optional<MailIntakeProperties> loadMailIntake() {
        return jdbc.sql("""
                SELECT enabled, protocol, host, port, username, password_encrypted, folder,
                       processed_folder, failed_folder, poll_interval_seconds,
                       max_messages_per_poll, max_attachment_size_kb, allowed_senders
                FROM mail_intake_settings WHERE id = 1
                """)
                .query(this::toMailIntakeProperties)
                .optional();
    }

    public void saveMailIntake(MailIntakeProperties settings) {
        String verschluesseltesPasswort = settings.password() == null || settings.password().isBlank()
                ? null
                : cipher.encrypt(settings.password());
        jdbc.sql("""
                INSERT INTO mail_intake_settings (id, enabled, protocol, host, port, username,
                                                   password_encrypted, folder, processed_folder,
                                                   failed_folder, poll_interval_seconds,
                                                   max_messages_per_poll, max_attachment_size_kb,
                                                   allowed_senders)
                VALUES (1, :enabled, :protocol, :host, :port, :username, :password, :folder,
                        :processedFolder, :failedFolder, :pollIntervalSeconds, :maxMessagesPerPoll,
                        :maxAttachmentSizeKb, :allowedSenders)
                ON CONFLICT (id) DO UPDATE SET
                    enabled                = excluded.enabled,
                    protocol               = excluded.protocol,
                    host                   = excluded.host,
                    port                   = excluded.port,
                    username               = excluded.username,
                    password_encrypted     = excluded.password_encrypted,
                    folder                 = excluded.folder,
                    processed_folder       = excluded.processed_folder,
                    failed_folder          = excluded.failed_folder,
                    poll_interval_seconds  = excluded.poll_interval_seconds,
                    max_messages_per_poll  = excluded.max_messages_per_poll,
                    max_attachment_size_kb = excluded.max_attachment_size_kb,
                    allowed_senders        = excluded.allowed_senders
                """)
                .param("enabled", settings.enabled())
                .param("protocol", settings.protocol())
                .param("host", settings.host())
                .param("port", settings.port())
                .param("username", settings.username())
                .param("password", verschluesseltesPasswort)
                .param("folder", settings.folder())
                .param("processedFolder", settings.processedFolder())
                .param("failedFolder", settings.failedFolder())
                .param("pollIntervalSeconds", settings.pollInterval().toSeconds())
                .param("maxMessagesPerPoll", settings.maxMessagesPerPoll())
                .param("maxAttachmentSizeKb", settings.maxAttachmentSize().toKilobytes())
                .param("allowedSenders", String.join(",", settings.allowedSenders()))
                .update();
    }

    private MailIntakeProperties toMailIntakeProperties(ResultSet row, int rowNumber) throws SQLException {
        String verschluesseltesPasswort = row.getString("password_encrypted");
        String passwort = verschluesseltesPasswort == null ? null : cipher.decrypt(verschluesseltesPasswort);
        String absender = row.getString("allowed_senders");
        return new MailIntakeProperties(row.getBoolean("enabled"), row.getString("protocol"), row.getString("host"),
                row.getInt("port"), row.getString("username"), passwort, row.getString("folder"),
                row.getString("processed_folder"), row.getString("failed_folder"),
                Duration.ofSeconds(row.getLong("poll_interval_seconds")), row.getInt("max_messages_per_poll"),
                DataSize.ofKilobytes(row.getLong("max_attachment_size_kb")), absenderListe(absender));
    }

    private static List<String> absenderListe(String kommagetrennt) {
        return kommagetrennt == null || kommagetrennt.isBlank()
                ? List.of()
                : Arrays.stream(kommagetrennt.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
