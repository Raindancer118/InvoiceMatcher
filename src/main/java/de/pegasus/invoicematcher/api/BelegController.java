package de.pegasus.invoicematcher.api;

import de.pegasus.invoicematcher.api.dto.ReviewCaseResponse;
import de.pegasus.invoicematcher.classification.ClassificationReport;
import de.pegasus.invoicematcher.classification.ClassificationService;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.review.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Belegannahme.
 *
 * <p>Zwei bewusst getrennte Wege: {@code /klassifikation} liefert nur das Ergebnis und legt
 * nichts ab - gedacht fuer die Vorschau im Frontend. Der Hauptpfad nimmt den Beleg an und
 * fuehrt ihn in den Pruefablauf.
 */
@RestController
@RequestMapping("/api/v1/belege")
public class BelegController {

    private final ClassificationService classification;
    private final ReviewService review;

    public BelegController(ClassificationService classification, ReviewService review) {
        this.classification = classification;
        this.review = review;
    }

    @PostMapping("/klassifikation")
    public ClassificationReport classify(@RequestParam("datei") MultipartFile datei) {
        return classification.classify(toSourceDocument(datei));
    }

    @PostMapping
    public ResponseEntity<ReviewCaseResponse> submit(@RequestParam("datei") MultipartFile datei) {
        ReviewCaseResponse response = ReviewCaseResponse.of(review.submit(toSourceDocument(datei)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Stapelverarbeitung, z.B. fuer den Inhalt eines Mailanhangs. */
    @PostMapping("/stapel")
    public ResponseEntity<List<ReviewCaseResponse>> submitBatch(@RequestParam("dateien") List<MultipartFile> dateien) {
        List<ReviewCaseResponse> responses = dateien.stream()
                .map(datei -> ReviewCaseResponse.of(review.submit(toSourceDocument(datei))))
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    private SourceDocument toSourceDocument(MultipartFile datei) {
        if (datei == null || datei.isEmpty()) {
            throw new IllegalArgumentException("Es wurde kein Beleg uebermittelt");
        }
        try {
            return new SourceDocument(datei.getOriginalFilename(), datei.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Beleg konnte nicht gelesen werden", e);
        }
    }
}
