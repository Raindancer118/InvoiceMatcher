package de.pegasus.invoicematcher.support;

import de.pegasus.invoicematcher.classification.ClassificationService;
import de.pegasus.invoicematcher.classification.ai.AiClassificationResponseParser;
import de.pegasus.invoicematcher.classification.ai.TieredInvoiceClassifier;
import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.config.AiProperties;
import de.pegasus.invoicematcher.extraction.ExtractionService;
import de.pegasus.invoicematcher.config.CompanyProperties;
import de.pegasus.invoicematcher.config.DatevExportProperties;
import de.pegasus.invoicematcher.export.DatevExportService;
import de.pegasus.invoicematcher.export.DocumentStore;
import de.pegasus.invoicematcher.export.InMemoryDocumentStore;
import de.pegasus.invoicematcher.persistence.ReportSerializer;
import de.pegasus.invoicematcher.persistence.SqliteDocumentStore;
import de.pegasus.invoicematcher.persistence.SqliteReviewRepository;
import de.pegasus.invoicematcher.review.ReviewRepository;
import de.pegasus.invoicematcher.export.datev.DocumentXmlWriter;
import de.pegasus.invoicematcher.export.datev.LedgerXmlWriter;
import de.pegasus.invoicematcher.export.datev.VatRateResolver;
import de.pegasus.invoicematcher.config.PartnerProperties;
import de.pegasus.invoicematcher.config.ReviewProperties;
import de.pegasus.invoicematcher.review.InMemoryReviewRepository;
import de.pegasus.invoicematcher.review.ReviewService;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Baut den produktiven Kontierungsrahmen fuer Unit-Tests auf - aus derselben
 * {@code application.yml}, die auch die Anwendung nutzt. So testen die Faelle dieselben
 * Vorgabewerte, mit denen auch die Einstellungen-Ablage beim allerersten Start gefuellt wird.
 *
 * <p>Die eigentliche Klassifikation trifft die KI (siehe {@code ClassificationService}); fuer
 * Tests ohne echten API-Zugriff wird ein {@link TieredInvoiceClassifier} mit einem
 * {@link FakeHttpTransport} verwendet, siehe {@link #classificationServiceWith}.
 */
public final class Kontierung {

    private static final StandardEnvironment ENVIRONMENT = loadApplicationYaml();

    private Kontierung() {
    }

    public static AccountingProperties accountingProperties() {
        return bind("invoicematcher.accounting", AccountingProperties.class);
    }

    public static CompanyProperties companyProperties() {
        return bind("invoicematcher.company", CompanyProperties.class);
    }

    public static PartnerProperties partnerProperties() {
        return bind("invoicematcher.partners", PartnerProperties.class);
    }

    public static AiProperties aiProperties() {
        return bind("invoicematcher.ai", AiProperties.class);
    }

    /**
     * Nutzt {@link CannedAiResponses} statt eines echten KI-Zugriffs: diese Tests pruefen
     * Downstream-Verhalten (Export, Pruefablauf, Persistenz), nicht die fachliche Richtigkeit
     * einer echten KI-Entscheidung. Fuer einen echten Anfrageversuch (ohne Schluessel im
     * Testumfeld = immer "nicht verfuegbar") siehe {@link #classificationServiceWithLiveAi}.
     */
    public static ClassificationService standardClassificationService() {
        return classificationServiceWith(ExtractionService.standard());
    }

    /** Derselbe Dienst mit einer eigenen Extraktion, etwa der vorgeschalteten Zeichenerkennung. */
    public static ClassificationService classificationServiceWith(ExtractionService extraction) {
        return classificationServiceWith(extraction, CannedAiResponses.classifier());
    }

    /** Fuer Tests, die die KI-Antwort selbst kontrollieren wollen. */
    public static ClassificationService classificationServiceWith(ExtractionService extraction,
                                                                   de.pegasus.invoicematcher.classification.ai.InvoiceClassifier classifier) {
        return new ClassificationService(extraction, classifier, new AiClassificationResponseParser(),
                new de.pegasus.invoicematcher.classification.ai.ClassificationRationaleService(classifier),
                Kontierung::companyProperties, Kontierung::accountingProperties, Kontierung::partnerProperties);
    }

    /** Der echte Klassifikationspfad gegen die in {@code application.yml} konfigurierten Schluessel. */
    public static ClassificationService classificationServiceWithLiveAi(ExtractionService extraction) {
        return classificationServiceWith(extraction, new TieredInvoiceClassifier(Kontierung::aiProperties));
    }

    public static DatevExportProperties datevProperties() {
        return bind("invoicematcher.datev", DatevExportProperties.class);
    }

    public static LedgerXmlWriter standardLedgerXmlWriter() {
        return new LedgerXmlWriter(datevProperties(), new VatRateResolver(Kontierung::accountingProperties));
    }

    public static DocumentXmlWriter standardDocumentXmlWriter() {
        return new DocumentXmlWriter(datevProperties());
    }

    /**
     * Der komplette Ablauf ohne Spring-Kontext: Klassifikation, Pruefung und DATEV-Export
     * teilen sich dieselbe Belegablage.
     */
    public record Workflow(ReviewService review, DatevExportService export) {
    }

    public static Workflow standardWorkflow() {
        InMemoryDocumentStore documents = new InMemoryDocumentStore();
        // Im Test laeuft die Klassifikation sofort im aufrufenden Faden: derselbe Ablauf wie
        // im Betrieb, nur ohne Warten auf einen Hintergrundfaden.
        ReviewService review = new ReviewService(standardClassificationService(),
                new InMemoryReviewRepository(),
                () -> new ReviewProperties(new java.math.BigDecimal("0.85"), null),
                reviewCase -> { },
                documents, Runnable::run);
        return new Workflow(review, new DatevExportService(review, documents,
                standardDocumentXmlWriter(), standardLedgerXmlWriter()));
    }

    /**
     * Derselbe Ablauf, aber mit der SQLite-Ablage - fuer Tests, die einen Neustart nachstellen.
     * {@link Persistence#close()} gibt die Datei wieder frei.
     */
    public record Persistence(ReviewService review, DatevExportService export,
                              ReviewRepository repository, DocumentStore documents,
                              com.zaxxer.hikari.HikariDataSource dataSource) implements AutoCloseable {

        @Override
        public void close() {
            dataSource.close();
        }
    }

    public static Persistence sqlitePersistence(java.nio.file.Path databaseFile) {
        com.zaxxer.hikari.HikariDataSource dataSource = new com.zaxxer.hikari.HikariDataSource();
        dataSource.setJdbcUrl("jdbc:sqlite:" + databaseFile);
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setMaximumPoolSize(1);
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("schema.sql")).execute(dataSource);

        var jdbc = org.springframework.jdbc.core.simple.JdbcClient.create(dataSource);
        var serializer = new ReportSerializer();
        ReviewRepository repository = new SqliteReviewRepository(jdbc, serializer);
        DocumentStore documents = new SqliteDocumentStore(jdbc);
        ReviewService review = new ReviewService(standardClassificationService(), repository,
                () -> new ReviewProperties(new java.math.BigDecimal("0.85"), null), reviewCase -> { },
                documents, Runnable::run);
        DatevExportService export = new DatevExportService(review, documents,
                standardDocumentXmlWriter(), standardLedgerXmlWriter());
        return new Persistence(review, export, repository, documents, dataSource);
    }

    private static <T> T bind(String prefix, Class<T> type) {
        return Binder.get(ENVIRONMENT).bind(prefix, type)
                .orElseThrow(() -> new IllegalStateException("Konfiguration '" + prefix + "' fehlt"));
    }

    private static StandardEnvironment loadApplicationYaml() {
        try {
            StandardEnvironment environment = new StandardEnvironment();
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"));
            sources.forEach(environment.getPropertySources()::addFirst);
            return environment;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
