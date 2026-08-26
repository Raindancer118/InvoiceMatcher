package de.pegasus.invoicematcher.support;

import de.pegasus.invoicematcher.classification.ClassificationService;
import de.pegasus.invoicematcher.classification.ConfidenceCalculator;
import de.pegasus.invoicematcher.config.AccountingProperties;
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
import de.pegasus.invoicematcher.masterdata.account.AccountAssignmentService;
import de.pegasus.invoicematcher.masterdata.account.DefaultExpenseAccountResolver;
import de.pegasus.invoicematcher.masterdata.account.FixedAssetIndicator;
import de.pegasus.invoicematcher.masterdata.account.KeywordExpenseAccountResolver;
import de.pegasus.invoicematcher.masterdata.account.RevenueAccountResolver;
import de.pegasus.invoicematcher.config.ReviewProperties;
import de.pegasus.invoicematcher.masterdata.partner.InMemoryPartnerRepository;
import de.pegasus.invoicematcher.review.InMemoryReviewRepository;
import de.pegasus.invoicematcher.review.ReviewService;
import de.pegasus.invoicematcher.masterdata.partner.PartnerResolver;
import de.pegasus.invoicematcher.rules.BookingLineAggregator;
import de.pegasus.invoicematcher.rules.RuleEngine;
import de.pegasus.invoicematcher.rules.impl.ArithmeticRule;
import de.pegasus.invoicematcher.rules.impl.BookingLineRule;
import de.pegasus.invoicematcher.rules.impl.DirectionRule;
import de.pegasus.invoicematcher.rules.impl.FixedAssetRule;
import de.pegasus.invoicematcher.rules.impl.InvoiceTitleRule;
import de.pegasus.invoicematcher.rules.impl.MandatoryFieldsRule;
import de.pegasus.invoicematcher.rules.impl.PersonenkontoRule;
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
 * {@code application.yml}, die auch die Anwendung nutzt. So testen die Faelle den echten
 * Kontenrahmen und nicht eine im Test nachgebaute Kopie.
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

    public static AccountAssignmentService standardAccountAssignment() {
        AccountingProperties properties = accountingProperties();
        return new AccountAssignmentService(List.of(
                new RevenueAccountResolver(properties),
                new KeywordExpenseAccountResolver(properties),
                new DefaultExpenseAccountResolver(properties)));
    }

    public static FixedAssetIndicator standardFixedAssetIndicator() {
        return new FixedAssetIndicator(accountingProperties());
    }

    public static PartnerResolver standardPartnerResolver() {
        PartnerProperties properties = partnerProperties();
        return new PartnerResolver(new InMemoryPartnerRepository(properties.toBusinessPartners()),
                properties.diverse().toRange());
    }

    public static RuleEngine standardRuleEngine() {
        AccountingProperties accounting = accountingProperties();
        return new RuleEngine(List.of(
                new InvoiceTitleRule(),
                new DirectionRule(companyProperties()),
                new MandatoryFieldsRule(),
                new ArithmeticRule(accounting),
                new FixedAssetRule(standardFixedAssetIndicator(), accounting),
                new PersonenkontoRule(standardPartnerResolver()),
                new BookingLineRule(new BookingLineAggregator(standardAccountAssignment()), accounting)));
    }

    public static ClassificationService standardClassificationService() {
        return classificationServiceWith(ExtractionService.standard());
    }

    /** Derselbe Dienst mit einer eigenen Extraktion, etwa der vorgeschalteten Zeichenerkennung. */
    public static ClassificationService classificationServiceWith(ExtractionService extraction) {
        return new ClassificationService(extraction, standardRuleEngine(), new ConfidenceCalculator());
    }

    public static DatevExportProperties datevProperties() {
        return bind("invoicematcher.datev", DatevExportProperties.class);
    }

    public static LedgerXmlWriter standardLedgerXmlWriter() {
        return new LedgerXmlWriter(datevProperties(), new VatRateResolver(accountingProperties()));
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
        ReviewService review = new ReviewService(standardClassificationService(),
                new InMemoryReviewRepository(),
                new ReviewProperties(new java.math.BigDecimal("0.85"), null),
                reviewCase -> { },
                documents);
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
                new ReviewProperties(new java.math.BigDecimal("0.85"), null), reviewCase -> { }, documents);
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
