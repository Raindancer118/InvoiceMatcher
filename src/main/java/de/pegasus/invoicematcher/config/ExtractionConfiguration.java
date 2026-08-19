package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.extraction.einvoice.EInvoiceXmlParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stellt die rahmenwerksfreien Bausteine der Extraktion als Beans bereit. */
@Configuration
public class ExtractionConfiguration {

    @Bean
    public EInvoiceXmlParser eInvoiceXmlParser() {
        return new EInvoiceXmlParser();
    }
}
