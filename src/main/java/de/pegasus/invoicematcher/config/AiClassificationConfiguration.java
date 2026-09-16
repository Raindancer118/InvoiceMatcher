package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.classification.ai.AiConnectionTester;
import de.pegasus.invoicematcher.classification.ai.AiKeyPool;
import de.pegasus.invoicematcher.classification.ai.InvoiceClassifier;
import de.pegasus.invoicematcher.classification.ai.TieredInvoiceClassifier;
import de.pegasus.invoicematcher.classification.ai.ZustandsAblage;
import de.pegasus.invoicematcher.persistence.SqliteDirectoryPreparer;
import io.github.raindancer118.aigateway.http.JdkHttpTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.Path;

/** Stellt den KI-Klassifikationspfad als Beans bereit. */
@Configuration
public class AiClassificationConfiguration {

    /**
     * Der Schluessel-Pool liegt neben der Datenbank: dort sichert der Betrieb ohnehin, und der
     * Zustand gehoert fachlich zur Ablage, nicht in ein zweites Verzeichnis. Verschluesselt,
     * weil er die Schluessel selbst enthaelt (siehe {@link ZustandsAblage}).
     */
    @Bean
    public AiKeyPool aiKeyPool(SecretCipher cipher, Environment environment) {
        Path verzeichnis = SqliteDirectoryPreparer
                .directoryOf(environment.getProperty("spring.datasource.url", ""))
                .orElse(Path.of("./data"));
        return new AiKeyPool(new JdkHttpTransport(), ZustandsAblage.in(verzeichnis, cipher));
    }

    @Bean
    public InvoiceClassifier invoiceClassifier(AiSettingsService aiSettings, AiKeyPool pool) {
        return new TieredInvoiceClassifier(aiSettings, pool);
    }

    @Bean
    public AiConnectionTester aiConnectionTester() {
        return new AiConnectionTester();
    }
}
