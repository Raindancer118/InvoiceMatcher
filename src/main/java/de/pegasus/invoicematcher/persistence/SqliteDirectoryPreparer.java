package de.pegasus.invoicematcher.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Legt das Verzeichnis der SQLite-Datei an, bevor die Datenquelle sie oeffnet.
 *
 * <p>SQLite erzeugt die Datenbankdatei selbst, aber kein fehlendes Verzeichnis. Ohne diesen
 * Schritt scheitert der erste Start auf einem frischen System - und genau dort, wo der Betrieb
 * einen Pfad wie {@code /var/lib/invoicematcher} konfiguriert hat, faellt es am spaetesten auf.
 *
 * <p>Als {@link BeanFactoryPostProcessor} umgesetzt, weil er vor jeder Bean laufen muss - auch
 * vor der automatisch konfigurierten Datenquelle.
 */
@Component
public class SqliteDirectoryPreparer implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SqliteDirectoryPreparer.class);

    private static final String URL_PROPERTY = "spring.datasource.url";
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";
    /** Speicher- und Sonderformen von SQLite haben kein Verzeichnis. */
    private static final String IN_MEMORY = ":memory:";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String url = beanFactory.getBean(Environment.class).getProperty(URL_PROPERTY, "");
        directoryOf(url).ifPresent(SqliteDirectoryPreparer::create);
    }

    static java.util.Optional<Path> directoryOf(String url) {
        if (!url.startsWith(SQLITE_PREFIX)) {
            return java.util.Optional.empty();
        }
        String file = url.substring(SQLITE_PREFIX.length());
        if (file.isBlank() || file.startsWith(IN_MEMORY) || file.startsWith("file:")) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(Path.of(file).toAbsolutePath().getParent());
    }

    private static void create(Path directory) {
        if (Files.isDirectory(directory)) {
            return;
        }
        try {
            Files.createDirectories(directory);
            log.info("Verzeichnis fuer die Ablage angelegt: {}", directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Verzeichnis fuer die SQLite-Ablage konnte nicht angelegt werden: "
                    + directory, e);
        }
    }
}
