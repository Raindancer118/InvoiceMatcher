package de.pegasus.invoicematcher.extraction.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Zeichenerkennung ueber das Kommandozeilenwerkzeug von Tesseract.
 *
 * <p>Bewusst der Prozessaufruf und keine JNA-Bindung: Tesseract wird als Systempaket
 * installiert, und eine native Bindung muesste zur jeweils installierten Bibliotheksversion
 * passen - ein Bruch, der erst zur Laufzeit auffiele. Der Prozessaufruf ist gegen jede
 * Tesseract-5-Fassung stabil und laesst sich vor dem ersten Beleg pruefen.
 *
 * <p>Die Aufloesung wird mitgegeben statt geschaetzt: ein gerastertes Seitenbild traegt keine
 * DPI-Angabe, und Tesseracts Schaetzung daneben kostete im Versuch die komplette Betragsspalte -
 * die Zahlen wurden erkannt, aber mit so niedriger Sicherheit, dass sie als Rauschen durchfielen.
 *
 * <p>Angefordert wird nicht die Textausgabe, sondern TSV: nur sie enthaelt die Wortrahmen, aus
 * denen {@link TesseractTsvLayout} die Spalten des Belegs zurueckgewinnt. Der Umweg ueber eine
 * Ausgabedatei statt {@code stdout} vermeidet die Konfigurationsdatei {@code configs/tsv}, die
 * bei einem abweichenden Datenverzeichnis nicht mehr gefunden wird.
 */
public class TesseractCliRecognizer implements OcrTextRecognizer {

    private static final Logger log = LoggerFactory.getLogger(TesseractCliRecognizer.class);

    private final String command;
    private final String language;
    private final String tessdataDir;
    private final int pageSegmentationMode;
    private final int dpi;
    private final Duration timeout;

    private volatile Boolean available;

    public TesseractCliRecognizer(String command, String language, String tessdataDir,
                                  int pageSegmentationMode, int dpi, Duration timeout) {
        this.command = command;
        this.language = language;
        this.tessdataDir = tessdataDir;
        this.pageSegmentationMode = pageSegmentationMode;
        this.dpi = dpi;
        this.timeout = timeout;
    }

    @Override
    public boolean isAvailable() {
        Boolean cached = available;
        if (cached == null) {
            cached = probe();
            available = cached;
        }
        return cached;
    }

    /**
     * Prueft nicht nur, ob das Werkzeug existiert, sondern auch, ob die eingestellten Sprachen
     * vorhanden sind. Ein fehlendes {@code deu.traineddata} faellt sonst erst am ersten Beleg
     * auf - und dann als Fehlklassifikation, nicht als Betriebsmeldung.
     */
    private boolean probe() {
        List<String> arguments = new ArrayList<>(List.of(command, "--list-langs"));
        addTessdataDir(arguments);
        try {
            ProcessResult result = run(arguments, null);
            if (result.exitCode() != 0) {
                log.warn("Zeichenerkennung nicht verfuegbar: '{}' meldet Code {}", command, result.exitCode());
                return false;
            }
            List<String> installed = result.output().lines().map(String::strip).toList();
            List<String> missing = languages().stream().filter(lang -> !installed.contains(lang)).toList();
            if (!missing.isEmpty()) {
                log.warn("Zeichenerkennung nicht verfuegbar: Sprachdaten fehlen: {}", missing);
                return false;
            }
            return true;
        } catch (OcrException | IOException e) {
            log.warn("Zeichenerkennung nicht verfuegbar: {}", e.getMessage());
            return false;
        }
    }

    List<String> languages() {
        return Arrays.stream(language.split("\\+")).map(String::strip).filter(part -> !part.isEmpty()).toList();
    }

    @Override
    public String recognize(BufferedImage page) {
        Path workingDirectory = createWorkingDirectory();
        try {
            Path image = workingDirectory.resolve("seite.png");
            ImageIO.write(page, "png", image.toFile());

            Path outputBase = workingDirectory.resolve("erkannt");
            List<String> arguments = new ArrayList<>(List.of(
                    command, image.toString(), outputBase.toString(),
                    "-l", language,
                    "--psm", String.valueOf(pageSegmentationMode),
                    "--dpi", String.valueOf(dpi),
                    "-c", "tessedit_create_tsv=1"));
            addTessdataDir(arguments);

            ProcessResult result = run(arguments, workingDirectory);
            Path tsv = outputBase.resolveSibling(outputBase.getFileName() + ".tsv");
            if (result.exitCode() != 0 || !Files.exists(tsv)) {
                throw new OcrException("Zeichenerkennung fehlgeschlagen (Code " + result.exitCode() + "): "
                        + result.output().strip());
            }
            return TesseractTsvLayout.toText(Files.readString(tsv, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new OcrException("Zeichenerkennung fehlgeschlagen: " + e.getMessage(), e);
        } finally {
            deleteRecursively(workingDirectory);
        }
    }

    private void addTessdataDir(List<String> arguments) {
        if (tessdataDir != null && !tessdataDir.isBlank()) {
            arguments.add("--tessdata-dir");
            arguments.add(tessdataDir);
        }
    }

    /**
     * Die Ausgabe geht in eine Datei statt in eine Pipe: aus einer Pipe muesste parallel
     * gelesen werden, sonst blockiert der Prozess am vollen Puffer - und genau dann wuerde die
     * Zeitgrenze nicht mehr greifen, fuer die sie da ist.
     */
    private ProcessResult run(List<String> arguments, Path workingDirectory) throws IOException {
        Path output = Files.createTempFile("invoicematcher-ocr-log-", ".txt");
        try {
            ProcessBuilder builder = new ProcessBuilder(arguments)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile());
            if (workingDirectory != null) {
                builder.directory(workingDirectory.toFile());
            }
            Process process = builder.start();
            try {
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    throw new OcrException("Zeichenerkennung ueberschritt " + timeout.toSeconds() + " s");
                }
                return new ProcessResult(process.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new OcrException("Zeichenerkennung wurde unterbrochen", e);
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static Path createWorkingDirectory() {
        try {
            return Files.createTempDirectory("invoicematcher-ocr-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Seitenbilder sind gross; sie duerfen das Dateisystem nicht ueberleben. */
    private static void deleteRecursively(Path directory) {
        try (var entries = Files.walk(directory)) {
            entries.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.debug("Aufraeumen fehlgeschlagen fuer {}", path);
                }
            });
        } catch (IOException e) {
            log.debug("Aufraeumen fehlgeschlagen fuer {}", directory);
        }
    }

    private record ProcessResult(int exitCode, String output) {
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "TesseractCliRecognizer[%s, -l %s]", command, language);
    }
}
