package de.pegasus.invoicematcher.persistence;

import de.pegasus.invoicematcher.review.ReviewCase;
import de.pegasus.invoicematcher.review.ReviewRepository;
import de.pegasus.invoicematcher.review.ReviewStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Pruefvorgaenge in der SQLite-Ablage. */
@Repository
public class SqliteReviewRepository implements ReviewRepository {

    private static final String UPSERT = """
            INSERT INTO review_case (id, status, filename, report_json, submitted_at,
                                     decided_at, decided_by, decision_note, exported_at)
            VALUES (:id, :status, :filename, :report, :submittedAt,
                    :decidedAt, :decidedBy, :decisionNote, :exportedAt)
            ON CONFLICT (id) DO UPDATE SET
                status        = excluded.status,
                filename      = excluded.filename,
                report_json   = excluded.report_json,
                decided_at    = excluded.decided_at,
                decided_by    = excluded.decided_by,
                decision_note = excluded.decision_note,
                exported_at   = excluded.exported_at
            """;

    private static final String SELECT = """
            SELECT id, status, report_json, submitted_at, decided_at, decided_by, decision_note, exported_at
            FROM review_case
            """;

    private final JdbcClient jdbc;
    private final ReportSerializer reports;

    public SqliteReviewRepository(JdbcClient jdbc, ReportSerializer reports) {
        this.jdbc = jdbc;
        this.reports = reports;
    }

    @Override
    public ReviewCase save(ReviewCase reviewCase) {
        jdbc.sql(UPSERT)
                .param("id", reviewCase.id())
                .param("status", reviewCase.status().name())
                .param("filename", reviewCase.report().filename())
                .param("report", reports.toJson(reviewCase.report()))
                .param("submittedAt", epochMilli(reviewCase.submittedAt()))
                .param("decidedAt", epochMilli(reviewCase.decidedAt()))
                .param("decidedBy", reviewCase.decidedBy())
                .param("decisionNote", reviewCase.decisionNote())
                .param("exportedAt", epochMilli(reviewCase.exportedAt()))
                .update();
        return reviewCase;
    }

    @Override
    public Optional<ReviewCase> findById(String id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("id", id)
                .query(this::toReviewCase)
                .optional();
    }

    @Override
    public List<ReviewCase> findAll() {
        return jdbc.sql(SELECT + " ORDER BY submitted_at DESC, id DESC")
                .query(this::toReviewCase)
                .list();
    }

    @Override
    public List<ReviewCase> findByStatus(ReviewStatus status) {
        return jdbc.sql(SELECT + " WHERE status = :status ORDER BY submitted_at DESC, id DESC")
                .param("status", status.name())
                .query(this::toReviewCase)
                .list();
    }

    private ReviewCase toReviewCase(ResultSet row, int rowNumber) throws SQLException {
        return new ReviewCase(
                row.getString("id"),
                reports.fromJson(row.getString("report_json")),
                ReviewStatus.valueOf(row.getString("status")),
                instant(row, "submitted_at"),
                instant(row, "decided_at"),
                row.getString("decided_by"),
                row.getString("decision_note"),
                instant(row, "exported_at"));
    }

    private static Long epochMilli(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : Instant.ofEpochMilli(value);
    }
}
