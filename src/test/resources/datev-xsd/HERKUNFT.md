# DATEV-Schemadateien

Offizielle Schemadateien der DATEV XML-Schnittstelle online, Version 6.0:

- `Document_v060.xsd` / `Document_types_v060.xsd` — Dokumentenverwaltungsdatei (`document.xml`)
- `Belegverwaltung_online_ledger_import_v060.xsd` / `Belegverwaltung_online_ledger_types_v060.xsd`
  — Belegsatzdatendatei (`LedgerImport`)

Sie liegen ausschließlich hier, um die vom `DatevExportService` erzeugten Dateien in den Tests
gegen das echte Schema zu validieren. Sie sind nicht Teil des Auslieferungsumfangs.

Bezogen über <https://github.com/ameax/datev-xml> (MIT), das die von DATEV veröffentlichten
Schemata spiegelt. Maßgeblich ist immer die Fassung im DATEV Developer Portal.
