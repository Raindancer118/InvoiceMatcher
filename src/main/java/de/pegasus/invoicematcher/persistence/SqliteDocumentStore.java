package de.pegasus.invoicematcher.persistence;

import de.pegasus.invoicematcher.export.DocumentStore;
import de.pegasus.invoicematcher.extraction.SourceDocument;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Belegbilder in der SQLite-Ablage.
 *
 * <p>Das Original liegt unveraendert als BLOB neben seinem Pruefvorgang - so bleibt der
 * Prototyp eine einzige Datei, die sich sichern und weitergeben laesst.
 */
@Repository
public class SqliteDocumentStore implements DocumentStore {

    private static final String UPSERT = """
            INSERT INTO document_image (case_id, filename, content)
            VALUES (:caseId, :filename, :content)
            ON CONFLICT (case_id) DO UPDATE SET
                filename = excluded.filename,
                content  = excluded.content
            """;

    private final JdbcClient jdbc;

    public SqliteDocumentStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void store(String caseId, SourceDocument document) {
        jdbc.sql(UPSERT)
                .param("caseId", caseId)
                .param("filename", document.filename())
                .param("content", document.content())
                .update();
    }

    @Override
    public Optional<SourceDocument> load(String caseId) {
        return jdbc.sql("SELECT filename, content FROM document_image WHERE case_id = :caseId")
                .param("caseId", caseId)
                .query((row, rowNumber) -> new SourceDocument(row.getString("filename"), row.getBytes("content")))
                .optional();
    }
}
