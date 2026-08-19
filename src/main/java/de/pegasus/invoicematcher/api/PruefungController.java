package de.pegasus.invoicematcher.api;

import de.pegasus.invoicematcher.api.dto.ApproveRequest;
import de.pegasus.invoicematcher.api.dto.RejectRequest;
import de.pegasus.invoicematcher.api.dto.ReviewCaseResponse;
import de.pegasus.invoicematcher.review.ReviewService;
import jakarta.validation.Valid;
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

    @PostMapping("/{id}/freigabe")
    public ReviewCaseResponse approve(@PathVariable String id, @Valid @RequestBody ApproveRequest request) {
        return ReviewCaseResponse.of(review.approve(id, request.benutzer(), request.toCorrection()));
    }

    @PostMapping("/{id}/ablehnung")
    public ReviewCaseResponse reject(@PathVariable String id, @Valid @RequestBody RejectRequest request) {
        return ReviewCaseResponse.of(review.reject(id, request.benutzer(), request.grund()));
    }
}
