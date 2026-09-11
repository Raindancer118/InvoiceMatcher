package de.pegasus.invoicematcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Zeichenerkennung fuer gescannte Belege.
 *
 * @param enabled              ausgeschaltet laeuft alles wie zuvor, gescannte Belege gehen zur Pruefung
 * @param command              das Tesseract-Werkzeug; ein Pfad, falls es nicht im PATH liegt
 * @param language             Sprachdaten, mehrere mit {@code +} verbunden (z.B. {@code deu+eng})
 * @param tessdataDir          abweichendes Verzeichnis der Sprachdaten; leer heisst Systemvorgabe
 * @param dpi                  Aufloesung, mit der die Seiten gerastert werden
 * @param maxPages             Obergrenze der gelesenen Seiten je Beleg
 * @param pageSegmentationMode Tesseracts {@code --psm}; 3 erkennt mehrspaltige Seiten selbst
 * @param minCharactersPerPage ab so vielen Zeichen gilt die Textebene des PDF als brauchbar
 * @param timeout              Zeitgrenze je Seite
 * @param gemini               Ausweichweg ueber eine Cloud-Zeichenerkennung fuer Seiten, deren
 *                             Titel Tesseract nicht lesen konnte (z.B. Handschrift)
 */
@ConfigurationProperties("invoicematcher.ocr")
public record OcrProperties(boolean enabled, String command, String language, String tessdataDir,
                            int dpi, int maxPages, int pageSegmentationMode,
                            int minCharactersPerPage, Duration timeout, Gemini gemini) {

    public OcrProperties {
        command = command == null || command.isBlank() ? "tesseract" : command;
        language = language == null || language.isBlank() ? "deu+eng" : language;
        dpi = dpi > 0 ? dpi : 300;
        maxPages = maxPages > 0 ? maxPages : 5;
        pageSegmentationMode = pageSegmentationMode > 0 ? pageSegmentationMode : 3;
        minCharactersPerPage = minCharactersPerPage > 0 ? minCharactersPerPage : 40;
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        gemini = gemini == null ? new Gemini(false, null, null, null) : gemini;
    }

    /**
     * @param enabled ausgeschaltet bleibt Tesseract die einzige Zeichenerkennung; kein Beleg
     *                verlaesst dann den eigenen Server. Erfordert bei Einschaltung einen
     *                Auftragsverarbeitungsvertrag mit dem Anbieter der API.
     * @param apiKey  Schluessel fuer die Gemini-API; ohne ihn bleibt der Ausweichweg inaktiv
     * @param model   das Gemini-Modell, z.B. {@code gemini-flash-latest}
     * @param timeout Zeitgrenze je Seite
     */
    public record Gemini(boolean enabled, String apiKey, String model, Duration timeout) {
        public Gemini {
            model = model == null || model.isBlank() ? "gemini-flash-latest" : model;
            timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        }
    }
}
