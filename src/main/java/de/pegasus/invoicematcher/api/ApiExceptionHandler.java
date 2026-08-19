package de.pegasus.invoicematcher.api;

import de.pegasus.invoicematcher.extraction.DocumentExtractionException;
import de.pegasus.invoicematcher.extraction.einvoice.UnsupportedEInvoiceException;
import de.pegasus.invoicematcher.review.ReviewNotFoundException;
import de.pegasus.invoicematcher.review.ReviewStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Uebersetzt fachliche Fehler in RFC-9457-Problemdokumente.
 *
 * <p>Ein unlesbarer Beleg ist kein Serverfehler, sondern eine fachliche Aussage - das Frontend
 * soll ihn dem Anwender erklaeren koennen und nicht auf einen Stacktrace treffen.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({DocumentExtractionException.class, UnsupportedEInvoiceException.class})
    public ProblemDetail handleUnreadableDocument(RuntimeException e) {
        log.info("Beleg konnte nicht verarbeitet werden: {}", e.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Beleg nicht lesbar", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Ungueltige Anfrage", e.getMessage());
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ProblemDetail handleUnknownReview(ReviewNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Pruefvorgang unbekannt", e.getMessage());
    }

    @ExceptionHandler(ReviewStateException.class)
    public ProblemDetail handleReviewState(ReviewStateException e) {
        return problem(HttpStatus.CONFLICT, "Pruefvorgang nicht mehr offen", e.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(detail);
        return problem;
    }
}
