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

-- Ueber die Einstellungen-Oberflaeche aenderbare Werte, die vorher nur per .env/Neustart
-- gingen. Je genau eine Zeile (CHECK erzwingt es); beim ersten Start durch den SettingsSeeder
-- aus application.yml/Umgebungsvariablen befuellt.
CREATE TABLE IF NOT EXISTS company_settings (
    id         INTEGER PRIMARY KEY CHECK (id = 1),
    name       TEXT NOT NULL,
    vat_id     TEXT,
    tax_number TEXT
);

-- password_encrypted ist AES-256-GCM-Ciphertext (SecretCipher), nie Klartext; NULL heisst
-- kein Passwort gesetzt. allowed_senders ist komma-getrennt wie MailIntakeProperties es schon
-- aus der Umgebungsvariable liest.
CREATE TABLE IF NOT EXISTS mail_intake_settings (
    id                     INTEGER PRIMARY KEY CHECK (id = 1),
    enabled                INTEGER NOT NULL,
    protocol               TEXT    NOT NULL,
    host                   TEXT    NOT NULL,
    port                   INTEGER NOT NULL,
    username               TEXT    NOT NULL,
    password_encrypted     TEXT,
    folder                 TEXT    NOT NULL,
    processed_folder       TEXT    NOT NULL,
    failed_folder          TEXT    NOT NULL,
    poll_interval_seconds  INTEGER NOT NULL,
    max_messages_per_poll  INTEGER NOT NULL,
    max_attachment_size_kb INTEGER NOT NULL,
    allowed_senders        TEXT    NOT NULL
);
