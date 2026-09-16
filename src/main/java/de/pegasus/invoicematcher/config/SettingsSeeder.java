package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.persistence.SettingsRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fuellt die Einstellungen-Ablage genau einmal aus den bisherigen {@code application.yml}/
 * Umgebungsvariablen-Werten - danach ist die Datenbank die Wahrheit, nicht mehr die Umgebung.
 *
 * <p>Laeuft erst, wenn das Schema steht ({@code spring.sql.init.mode: always} ist beim
 * Application-Start bereits durchgelaufen). Eine bereits befuellte Tabelle bleibt unangetastet -
 * sonst wuerde jeder Neustart eine ueber die Oberflaeche vorgenommene Aenderung wieder verwerfen.
 */
@Component
public class SettingsSeeder implements ApplicationRunner {

    private final SettingsRepository repository;
    private final CompanyProperties companyVorgabewerte;
    private final MailIntakeProperties mailVorgabewerte;
    private final AccountingProperties accountingVorgabewerte;
    private final PartnerProperties partnerVorgabewerte;
    private final AiProperties aiVorgabewerte;
    private final ReviewProperties reviewVorgabewerte;

    public SettingsSeeder(SettingsRepository repository, CompanyProperties companyVorgabewerte,
                          MailIntakeProperties mailVorgabewerte, AccountingProperties accountingVorgabewerte,
                          PartnerProperties partnerVorgabewerte, AiProperties aiVorgabewerte,
                          ReviewProperties reviewVorgabewerte) {
        this.repository = repository;
        this.companyVorgabewerte = companyVorgabewerte;
        this.mailVorgabewerte = mailVorgabewerte;
        this.accountingVorgabewerte = accountingVorgabewerte;
        this.partnerVorgabewerte = partnerVorgabewerte;
        this.aiVorgabewerte = aiVorgabewerte;
        this.reviewVorgabewerte = reviewVorgabewerte;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.loadCompany().isEmpty()) {
            repository.saveCompany(companyVorgabewerte);
        }
        if (repository.loadMailIntake().isEmpty()) {
            repository.saveMailIntake(mailVorgabewerte);
        }
        if (repository.loadAccounting().isEmpty()) {
            repository.saveAccounting(accountingVorgabewerte);
        }
        if (repository.loadPartners().isEmpty()) {
            repository.savePartners(partnerVorgabewerte);
        }
        if (repository.loadAi().isEmpty()) {
            repository.saveAi(aiVorgabewerte);
        }
        if (repository.loadReviewThreshold().isEmpty()) {
            repository.saveReview(reviewVorgabewerte.autoApproveThreshold());
        }
    }
}
