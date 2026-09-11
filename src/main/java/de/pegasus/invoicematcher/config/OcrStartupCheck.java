package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.extraction.ocr.OcrTextRecognizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Meldet beim Start, ob gescannte Belege gelesen werden koennen.
 *
 * <p>Ohne diese Meldung faellt ein fehlendes Tesseract erst am ersten Scan auf - und dann als
 * Beleg mit leeren Feldern in der Pruefliste, also als scheinbar fachlicher Mangel. Der Start
 * bricht bewusst nicht ab: die uebrigen Belegformate laufen davon unberuehrt weiter.
 */
@Component
public class OcrStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(OcrStartupCheck.class);

    private final OcrProperties properties;
    private final OcrTextRecognizer recognizer;
    private final AiSettingsService aiSettings;

    public OcrStartupCheck(OcrProperties properties, OcrTextRecognizer recognizer, AiSettingsService aiSettings) {
        this.properties = properties;
        this.recognizer = recognizer;
        this.aiSettings = aiSettings;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        if (!properties.enabled()) {
            log.info("Zeichenerkennung abgeschaltet - gescannte Belege gehen zur Pruefung");
            return;
        }
        if (recognizer.isAvailable()) {
            log.info("Zeichenerkennung bereit: {} ({} dpi)", recognizer, properties.dpi());
        } else {
            log.warn("Zeichenerkennung eingeschaltet, aber nicht ausfuehrbar ('{}', Sprachen '{}') - "
                    + "gescannte Belege gehen mit leeren Feldern zur Pruefung",
                    properties.command(), properties.language());
        }
        AiProperties.Tier smart = aiSettings.current().smart();
        if (smart.apiKeys().isEmpty()) {
            log.warn("Gemini-Ausweichweg ohne API-Schluessel - "
                    + "Belege mit unlesbarem Titel (z.B. Handschrift) landen weiterhin bei 'Sonstige'");
        } else {
            log.info("Gemini-Ausweichweg bereit: Modell '{}', {} Schluessel im Pool - wird nur bei "
                    + "Belegen ohne erkennbaren Titel angefragt", smart.model(), smart.apiKeys().size());
        }
    }
}
