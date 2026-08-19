-- Ablage des Prototyps. Bewusst schlank: der Pruefvorgang ist die einzige veraenderliche
-- Groesse, das Klassifikationsergebnis wird als Ganzes aufbewahrt.
--
-- Beim Wechsel auf eine Server-Datenbank tritt hier ein Migrationswerkzeug an die Stelle
-- dieser Datei; fuer eine einzelne Schemafassung waere es Ballast.

CREATE TABLE IF NOT EXISTS review_case (
    id            TEXT PRIMARY KEY,
    status        TEXT    NOT NULL,
    filename      TEXT    NOT NULL,
    report_json   TEXT    NOT NULL,
    submitted_at  INTEGER NOT NULL,
    decided_at    INTEGER,
    decided_by    TEXT,
    decision_note TEXT,
    exported_at   INTEGER
);

-- Nach offenen Vorgaengen und nach Exportkandidaten wird bei jedem Aufruf der Pruefliste
-- gesucht; ohne Index waere das ein Full Scan ueber alle je verarbeiteten Belege.
CREATE INDEX IF NOT EXISTS idx_review_case_status ON review_case (status, submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_case_exported ON review_case (exported_at);

-- Das Belegbild getrennt, damit die Pruefliste nicht bei jedem Zugriff Megabytes mitliest.
CREATE TABLE IF NOT EXISTS document_image (
    case_id  TEXT PRIMARY KEY,
    filename TEXT NOT NULL,
    content  BLOB NOT NULL,
    FOREIGN KEY (case_id) REFERENCES review_case (id) ON DELETE CASCADE
);
