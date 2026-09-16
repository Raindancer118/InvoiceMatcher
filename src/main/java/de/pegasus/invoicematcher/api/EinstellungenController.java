package de.pegasus.invoicematcher.api;

import de.pegasus.invoicematcher.api.dto.FirmaRequest;
import de.pegasus.invoicematcher.api.dto.FirmaResponse;
import de.pegasus.invoicematcher.api.dto.GeschaeftspartnerRequest;
import de.pegasus.invoicematcher.api.dto.GeschaeftspartnerResponse;
import de.pegasus.invoicematcher.api.dto.KiRequest;
import de.pegasus.invoicematcher.api.dto.KiResponse;
import de.pegasus.invoicematcher.api.dto.KiTestRequest;
import de.pegasus.invoicematcher.api.dto.KiTestResponse;
import de.pegasus.invoicematcher.api.dto.KontenrahmenRequest;
import de.pegasus.invoicematcher.api.dto.KontenrahmenResponse;
import de.pegasus.invoicematcher.api.dto.MailEingangRequest;
import de.pegasus.invoicematcher.api.dto.MailEingangResponse;
import de.pegasus.invoicematcher.api.dto.PruefungRequest;
import de.pegasus.invoicematcher.api.dto.PruefungResponse;
import de.pegasus.invoicematcher.classification.ai.AiConnectionTester;
import de.pegasus.invoicematcher.classification.ai.AiKeyStatus;
import de.pegasus.invoicematcher.classification.ai.AiKeyStatusService;
import de.pegasus.invoicematcher.config.AccountingSettingsService;
import de.pegasus.invoicematcher.config.AiSettingsService;
import de.pegasus.invoicematcher.config.CompanySettingsService;
import de.pegasus.invoicematcher.config.MailIntakeSettingsService;
import de.pegasus.invoicematcher.config.PartnerSettingsService;
import de.pegasus.invoicematcher.config.ReviewSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Firmendaten, Belegannahme per Mail, Kontenrahmen, Geschaeftspartner und KI-Modellwahl - vorher
 * nur per .env und Neustart aenderbar.
 */
@RestController
@RequestMapping("/api/v1/einstellungen")
public class EinstellungenController {

    private final CompanySettingsService company;
    private final MailIntakeSettingsService mail;
    private final AccountingSettingsService accounting;
    private final PartnerSettingsService partners;
    private final AiSettingsService ai;
    private final AiConnectionTester connectionTester;
    private final AiKeyStatusService keyStatus;
    private final ReviewSettingsService review;

    public EinstellungenController(CompanySettingsService company, MailIntakeSettingsService mail,
                                   AccountingSettingsService accounting, PartnerSettingsService partners,
                                   AiSettingsService ai, AiConnectionTester connectionTester,
                                   AiKeyStatusService keyStatus, ReviewSettingsService review) {
        this.company = company;
        this.mail = mail;
        this.accounting = accounting;
        this.partners = partners;
        this.ai = ai;
        this.connectionTester = connectionTester;
        this.keyStatus = keyStatus;
        this.review = review;
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

    @GetMapping("/kontenrahmen")
    public KontenrahmenResponse kontenrahmen() {
        return KontenrahmenResponse.of(accounting.current());
    }

    @PutMapping("/kontenrahmen")
    public KontenrahmenResponse kontenrahmenSpeichern(@Valid @RequestBody KontenrahmenRequest request) {
        return KontenrahmenResponse.of(accounting.update(request.toAccountingProperties()));
    }

    @GetMapping("/geschaeftspartner")
    public GeschaeftspartnerResponse geschaeftspartner() {
        return GeschaeftspartnerResponse.of(partners.current());
    }

    @PutMapping("/geschaeftspartner")
    public GeschaeftspartnerResponse geschaeftspartnerSpeichern(
            @Valid @RequestBody GeschaeftspartnerRequest request) {
        return GeschaeftspartnerResponse.of(partners.update(request.toPartnerProperties()));
    }

    /** Die Schwelle, ab der ein Beleg ohne menschliche Pruefung durchlaeuft. */
    @GetMapping("/pruefung")
    public PruefungResponse pruefung() {
        return PruefungResponse.of(review.current());
    }

    @PutMapping("/pruefung")
    public PruefungResponse pruefungSpeichern(@Valid @RequestBody PruefungRequest request) {
        return PruefungResponse.of(review.update(request.freigabeschwelle()));
    }

    @GetMapping("/ki")
    public KiResponse ki() {
        return KiResponse.of(ai.current());
    }

    @PutMapping("/ki")
    public KiResponse kiSpeichern(@Valid @RequestBody KiRequest request) {
        return KiResponse.of(ai.update(request.toAiProperties()));
    }

    /**
     * Der Zustand jedes einzelnen Schluessels, wie ihn der laufende Pool kennt - kostenlos,
     * ohne Anfrage an einen Anbieter.
     */
    @GetMapping("/ki/schluessel")
    public List<AiKeyStatus> kiSchluessel() {
        return keyStatus.status();
    }

    /**
     * Derselbe Zustand mit einer Lebendpruefung je Schluessel: erreichbar, wie viel Kontingent
     * noch da ist und wann es zurueckkommt. Kostet bei Groq ein paar Token je Schluessel,
     * laeuft deshalb nur auf ausdrueckliche Anforderung ({@code POST}, nicht {@code GET}).
     */
    @PostMapping("/ki/schluessel/pruefen")
    public List<AiKeyStatus> kiSchluesselPruefen() {
        return keyStatus.pruefe();
    }

    @PostMapping("/ki/test")
    public KiTestResponse kiTesten(@RequestBody KiTestRequest request) {
        AiConnectionTester.Result fast = connectionTester.testGroq(request.fastModell(), request.fastSchluessel());
        AiConnectionTester.Result smart = connectionTester.testGemini(request.smartModell(), request.smartSchluessel());
        return new KiTestResponse(fast.ok(), fast.meldung(), smart.ok(), smart.meldung());
    }
}
