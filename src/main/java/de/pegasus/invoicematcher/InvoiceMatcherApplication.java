package de.pegasus.invoicematcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Startpunkt des Belegdienstes. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class InvoiceMatcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceMatcherApplication.class, args);
    }
}
