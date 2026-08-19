package de.pegasus.invoicematcher.api;

import de.pegasus.invoicematcher.api.dto.ExportRequest;
import de.pegasus.invoicematcher.api.dto.ReviewCaseResponse;
import de.pegasus.invoicematcher.export.DatevExportService;
import de.pegasus.invoicematcher.export.DatevPackage;
import de.pegasus.invoicematcher.review.ReviewService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Uebergabe an DATEV.
 *
 * <p>Liefert ein DATEV Document-Package als ZIP. Jeder enthaltene Vorgang wird dabei als
 * exportiert vermerkt, damit derselbe Beleg nicht ein zweites Mal gebucht werden kann.
 */
@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private final DatevExportService export;
    private final ReviewService review;

    public ExportController(DatevExportService export, ReviewService review) {
        this.export = export;
        this.review = review;
    }

    @GetMapping("/offen")
    public List<ReviewCaseResponse> pending() {
        return review.exportableCases().stream().map(ReviewCaseResponse::of).toList();
    }

    @PostMapping("/datev")
    public ResponseEntity<byte[]> exportToDatev(@RequestBody(required = false) ExportRequest request) {
        DatevPackage datevPackage = request == null || request.isEmpty()
                ? export.exportPending()
                : export.export(request.vorgaenge());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "zip"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(datevPackage.filename()).build());
        return ResponseEntity.ok().headers(headers).body(datevPackage.content());
    }
}
