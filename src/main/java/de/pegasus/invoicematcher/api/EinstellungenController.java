package de.pegasus.invoicematcher.api;

import de.pegasus.invoicematcher.api.dto.FirmaRequest;
import de.pegasus.invoicematcher.api.dto.FirmaResponse;
import de.pegasus.invoicematcher.api.dto.MailEingangRequest;
import de.pegasus.invoicematcher.api.dto.MailEingangResponse;
import de.pegasus.invoicematcher.config.CompanySettingsService;
import de.pegasus.invoicematcher.config.MailIntakeSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Firmendaten und Belegannahme per Mail - vorher nur per .env und Neustart aenderbar. */
@RestController
@RequestMapping("/api/v1/einstellungen")
public class EinstellungenController {

    private final CompanySettingsService company;
    private final MailIntakeSettingsService mail;

    public EinstellungenController(CompanySettingsService company, MailIntakeSettingsService mail) {
        this.company = company;
        this.mail = mail;
    }

    @GetMapping("/firma")
    public FirmaResponse firma() {
        return FirmaResponse.of(company.current());
    }

    @PutMapping("/firma")
    public FirmaResponse firmaSpeichern(@Valid @RequestBody FirmaRequest request) {
        return FirmaResponse.of(company.update(request.toCompanyProperties()));
    }

    @GetMapping("/mail-eingang")
    public MailEingangResponse mailEingang() {
        return MailEingangResponse.of(mail.current());
    }

    @PutMapping("/mail-eingang")
    public MailEingangResponse mailEingangSpeichern(@Valid @RequestBody MailEingangRequest request) {
        return MailEingangResponse.of(mail.update(request.toMailIntakeProperties()));
    }
}
