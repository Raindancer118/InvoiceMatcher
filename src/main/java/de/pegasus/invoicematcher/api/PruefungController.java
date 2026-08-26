package de.pegasus.invoicematcher.api;

import de.pegasus.invoicematcher.api.dto.ApproveRequest;
import de.pegasus.invoicematcher.api.dto.RejectRequest;
import de.pegasus.invoicematcher.api.dto.ReviewCaseResponse;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import de.pegasus.invoicematcher.review.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Der Human-in-the-Loop: offene Vorgaenge einsehen, freigeben oder ablehnen. */
@RestController
@RequestMapping("/api/v1/pruefungen")
public class PruefungController {

    private final ReviewService review;

    public PruefungController(ReviewService review) {
        this.review = review;
    }

    @GetMapping
    public List<ReviewCaseResponse> list(@RequestParam(value = "alle", defaultValue = "false") boolean alle) {
        return (alle ? review.allCases() : review.openCases()).stream()
                .map(ReviewCaseResponse::of)
                .toList();
    }

    @GetMapping("/{id}")
    public ReviewCaseResponse find(@PathVariable String id) {
        return ReviewCaseResponse.of(review.find(id));
    }

    /**
     * Das Original zum Vorgang, zur Anzeige neben der Vorkontierung.
     *
     * <p>{@code inline} statt {@code attachment}: der Beleg gehoert in den Pruefblick, nicht in
     * den Download-Ordner.
     */
    @GetMapping("/{id}/beleg")
    public ResponseEntity<byte[]> document(@PathVariable String id) {
        SourceDocument document = review.document(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaTypeOf(document));
        headers.setContentDisposition(ContentDisposition.inline().filename(document.filename()).build());
        return ResponseEntity.ok().headers(headers).body(document.content());
    }

    /** Der Inhalt entscheidet, nicht die Dateiendung - Mailanhaenge sind unzuverlaessig benannt. */
    private static MediaType mediaTypeOf(SourceDocument document) {
        if (document.isPdf()) {
            return MediaType.APPLICATION_PDF;
        }
        return document.isXml() ? MediaType.APPLICATION_XML : MediaType.APPLICATION_OCTET_STREAM;
    }

    @PostMapping("/{id}/freigabe")
    public ReviewCaseResponse approve(@PathVariable String id, @Valid @RequestBody ApproveRequest request) {
        return ReviewCaseResponse.of(review.approve(id, request.benutzer(), request.toCorrection()));
    }

    @PostMapping("/{id}/ablehnung")
    public ReviewCaseResponse reject(@PathVariable String id, @Valid @RequestBody RejectRequest request) {
        return ReviewCaseResponse.of(review.reject(id, request.benutzer(), request.grund()));
    }
}
