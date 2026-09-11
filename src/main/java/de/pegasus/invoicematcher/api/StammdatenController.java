package de.pegasus.invoicematcher.api;

import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.config.AccountingSettingsService;
import de.pegasus.invoicematcher.config.PartnerSettingsService;
import de.pegasus.invoicematcher.masterdata.partner.BusinessPartner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Nachschlagedaten fuer die Oberflaeche.
 *
 * <p>Damit kann das Frontend Kontonummern und Partner benennen, statt sie zu duplizieren - der
 * Kontierungsrahmen bleibt an einer Stelle gepflegt (Einstellungen-Oberflaeche).
 */
@RestController
@RequestMapping("/api/v1/stammdaten")
public class StammdatenController {

    private final PartnerSettingsService partners;
    private final AccountingSettingsService accounting;

    public StammdatenController(PartnerSettingsService partners, AccountingSettingsService accounting) {
        this.partners = partners;
        this.accounting = accounting;
    }

    @GetMapping("/partner")
    public List<BusinessPartner> partners() {
        return partners.current().toBusinessPartners().stream()
                .sorted((a, b) -> a.direction() == b.direction() ? Integer.compare(a.konto(), b.konto())
                        : a.direction().compareTo(b.direction()))
                .toList();
    }

    @GetMapping("/kategorien")
    public List<AccountingProperties.ExpenseCategory> categories() {
        return accounting.current().categories();
    }
}
