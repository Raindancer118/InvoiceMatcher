package de.pegasus.invoicematcher.support;

import de.pegasus.invoicematcher.classification.ai.InvoiceClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Ersetzt die echte KI-Anbindung in {@code @SpringBootTest}-Klassen (Profil "test") durch
 * {@link CannedAiResponses}: diese Tests pruefen die REST-API und den Pruef-/Exportablauf, nicht
 * die fachliche Richtigkeit einer echten KI-Entscheidung, und sollen ohne API-Schluessel laufen.
 */
@Configuration
@Profile("test")
public class TestAiConfiguration {

    @Bean
    @Primary
    public InvoiceClassifier fakeInvoiceClassifier() {
        return CannedAiResponses.classifier();
    }
}
