package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.classification.ai.AiConnectionTester;
import de.pegasus.invoicematcher.classification.ai.InvoiceClassifier;
import de.pegasus.invoicematcher.classification.ai.TieredInvoiceClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stellt den KI-Klassifikationspfad als Beans bereit. */
@Configuration
public class AiClassificationConfiguration {

    @Bean
    public InvoiceClassifier invoiceClassifier(AiSettingsService aiSettings) {
        return new TieredInvoiceClassifier(aiSettings);
    }

    @Bean
    public AiConnectionTester aiConnectionTester() {
        return new AiConnectionTester();
    }
}
