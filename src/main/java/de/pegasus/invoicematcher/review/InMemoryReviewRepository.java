package de.pegasus.invoicematcher.review;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Speicherbasierte Ablage.
 *
 * <p>Bewusst keine Spring-Bean: produktiv laeuft die SQLite-Ablage, diese Fassung dient den
 * Modultests, die ohne Datei und ohne Anwendungskontext auskommen sollen.
 */
public class InMemoryReviewRepository implements ReviewRepository {

    private final Map<String, ReviewCase> cases = new ConcurrentHashMap<>();

    @Override
    public ReviewCase save(ReviewCase reviewCase) {
        cases.put(reviewCase.id(), reviewCase);
        return reviewCase;
    }

    @Override
    public Optional<ReviewCase> findById(String id) {
        return Optional.ofNullable(cases.get(id));
    }

    @Override
    public List<ReviewCase> findAll() {
        return cases.values().stream()
                .sorted(Comparator.comparing(ReviewCase::submittedAt).reversed())
                .toList();
    }

    @Override
    public List<ReviewCase> findByStatus(ReviewStatus status) {
        return findAll().stream().filter(reviewCase -> reviewCase.status() == status).toList();
    }
}
