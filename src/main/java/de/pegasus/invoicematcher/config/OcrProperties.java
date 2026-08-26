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
 */
@ConfigurationProperties("invoicematcher.ocr")
public record OcrProperties(boolean enabled, String command, String language, String tessdataDir,
                            int dpi, int maxPages, int pageSegmentationMode,
                            int minCharactersPerPage, Duration timeout) {

    public OcrProperties {
        command = command == null || command.isBlank() ? "tesseract" : command;
        language = language == null || language.isBlank() ? "deu+eng" : language;
        dpi = dpi > 0 ? dpi : 300;
        maxPages = maxPages > 0 ? maxPages : 5;
        pageSegmentationMode = pageSegmentationMode > 0 ? pageSegmentationMode : 3;
        minCharactersPerPage = minCharactersPerPage > 0 ? minCharactersPerPage : 40;
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }
}
