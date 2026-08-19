package de.pegasus.invoicematcher.api;

import de.pegasus.invoicematcher.api.dto.RuleDescription;
import de.pegasus.invoicematcher.config.AccountingProperties;
import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.masterdata.partner.BusinessPartner;
import de.pegasus.invoicematcher.masterdata.partner.PartnerRepository;
import de.pegasus.invoicematcher.rules.RuleEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Nachschlagedaten fuer die Oberflaeche.
 *
 * <p>Damit kann das Frontend Kontonummern, Partner und Regeln benennen, statt sie zu
 * duplizieren - der Kontierungsrahmen bleibt an einer Stelle gepflegt.
 */
@RestController
@RequestMapping("/api/v1/stammdaten")
public class StammdatenController {

    private final PartnerRepository partners;
    private final AccountingProperties accounting;
    private final RuleEngine ruleEngine;

    public StammdatenController(PartnerRepository partners, AccountingProperties accounting,
                                RuleEngine ruleEngine) {
        this.partners = partners;
        this.accounting = accounting;
        this.ruleEngine = ruleEngine;
    }

    @GetMapping("/partner")
    public List<BusinessPartner> partners() {
        return Arrays.stream(Direction.values()).flatMap(direction -> partners.findAll(direction).stream()).toList();
    }

    @GetMapping("/kategorien")
    public List<AccountingProperties.ExpenseCategory> categories() {
        return accounting.categories();
    }

    @GetMapping("/regeln")
    public List<RuleDescription> rules() {
        return ruleEngine.rules().stream().map(RuleDescription::of).toList();
    }
}
