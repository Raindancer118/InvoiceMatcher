package de.pegasus.invoicematcher.export;

import de.pegasus.invoicematcher.review.ReviewCase;
import de.pegasus.invoicematcher.review.ReviewService;
import de.pegasus.invoicematcher.support.Belege;
import de.pegasus.invoicematcher.support.DatevSchema;
import de.pegasus.invoicematcher.support.Kontierung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatevExportServiceTest {

    private ReviewService review;
    private DatevExportService export;

    @BeforeEach
    void setUp() {
        Kontierung.Workflow workflow = Kontierung.standardWorkflow();
        this.review = workflow.review();
        this.export = workflow.export();
    }

    private static Map<String, byte[]> entriesOf(byte[] zip) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        } catch (Exception e) {
            throw new AssertionError("ZIP konnte nicht gelesen werden", e);
        }
        return entries;
    }

    private static String text(Map<String, byte[]> entries, String name) {
        return new String(entries.get(name), StandardCharsets.UTF_8);
    }

    @Test
    void packagesDocumentImageAndBookingDataTogether() {
        review.submit(Belege.document("01_PDF_Klaus_Furniere.pdf"));

        Map<String, byte[]> entries = entriesOf(export.exportPending().content());

        assertThat(entries).containsKey("document.xml");
        assertThat(entries).containsKey("01_PDF_Klaus_Furniere.pdf");
        assertThat(entries.keySet()).anyMatch(name -> name.startsWith("Rechnungsdaten_"));
        DatevSchema.assertValid(DatevSchema.Kind.DOCUMENT, text(entries, "document.xml"));
    }

    @Test
    void theDocumentImageIsTheUnchangedOriginalFile() {
        review.submit(Belege.document("01_PDF_Klaus_Furniere.pdf"));

        Map<String, byte[]> entries = entriesOf(export.exportPending().content());

        assertThat(entries.get("01_PDF_Klaus_Furniere.pdf"))
                .isEqualTo(Belege.bytes("01_PDF_Klaus_Furniere.pdf"));
    }

    @Test
    void aSonstigeDocumentIsFiledWithoutBookingData() {
        review.submit(Belege.document("18_Sonstige_Mietvertrag.pdf"));

        Map<String, byte[]> entries = entriesOf(export.exportPending().content());

        assertThat(entries).containsKey("18_Sonstige_Mietvertrag.pdf");
        assertThat(entries.keySet()).noneMatch(name -> name.startsWith("Rechnungsdaten_"));
        assertThat(text(entries, "document.xml")).doesNotContain("Ledger");
    }

    @Test
    void documentsAwaitingReviewAreNotExported() {
        review.submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));

        assertThatThrownBy(() -> export.exportPending())
                .isInstanceOf(NothingToExportException.class);
    }

    @Test
    void anApprovedDocumentBecomesExportable() {
        ReviewCase open = review.submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));
        review.approve(open.id(), "tom", null);

        Map<String, byte[]> entries = entriesOf(export.exportPending().content());

        assertThat(entries).containsKey("05_PDF_Buerobedarf_Kopierer.pdf");
        // weiterhin "zur Pruefung", also nur Ablage ohne Buchungssatz
        assertThat(entries.keySet()).noneMatch(name -> name.startsWith("Rechnungsdaten_"));
    }

    @Test
    void aRejectedDocumentIsNeverExported() {
        ReviewCase open = review.submit(Belege.document("29_konstruiert_Rechenfehler.pdf"));
        review.reject(open.id(), "tom", "Beleg neu anfordern");

        assertThatThrownBy(() -> export.exportPending()).isInstanceOf(NothingToExportException.class);
    }

    @Test
    void anExportedDocumentIsNotExportedASecondTime() {
        review.submit(Belege.document("01_PDF_Klaus_Furniere.pdf"));
        export.exportPending();

        assertThatThrownBy(() -> export.exportPending())
                .isInstanceOf(NothingToExportException.class)
                .hasMessageContaining("bereits exportiert");
    }

    @Test
    void marksExportedCasesSoTheyCannotBeBookedTwice() {
        ReviewCase submitted = review.submit(Belege.document("01_PDF_Klaus_Furniere.pdf"));

        export.exportPending();

        assertThat(review.find(submitted.id()).exportedAt()).isNotNull();
    }

    @Test
    void explicitlyReexportsSelectedDocumentsWithoutChangingTheirFirstExportDate() {
        ReviewCase submitted = review.submit(Belege.document("01_PDF_Klaus_Furniere.pdf"));
        export.exportPending();
        var firstExport = review.find(submitted.id()).exportedAt();

        DatevPackage repeated = export.export(List.of(submitted.id(), submitted.id()), true);

        assertThat(repeated.caseIds()).containsExactly(submitted.id());
        assertThat(entriesOf(repeated.content())).containsKey("01_PDF_Klaus_Furniere.pdf");
        assertThat(review.find(submitted.id()).exportedAt()).isEqualTo(firstExport);
        assertThatThrownBy(() -> export.exportPending()).isInstanceOf(NothingToExportException.class);
    }

    @Test
    void reexportDoesNotAllowUnapprovedDocuments() {
        ReviewCase open = review.submit(Belege.document("05_PDF_Buerobedarf_Kopierer.pdf"));
        assertThatThrownBy(() -> export.export(List.of(open.id()), true))
                .isInstanceOf(NothingToExportException.class);
        review.reject(open.id(), "tom", "Unvollständig");
        assertThatThrownBy(() -> export.export(List.of(open.id()), true))
                .isInstanceOf(NothingToExportException.class);
    }

    @Test
    void bundlesSeveralDocumentsIntoOnePackage() {
        review.submit(Belege.document("01_PDF_Klaus_Furniere.pdf"));
        review.submit(Belege.document("16_AR_Laub.pdf"));
        review.submit(Belege.document("18_Sonstige_Mietvertrag.pdf"));

        Map<String, byte[]> entries = entriesOf(export.exportPending().content());
        String documentXml = text(entries, "document.xml");

        DatevSchema.assertValid(DatevSchema.Kind.DOCUMENT, documentXml);
        assertThat(documentXml).contains("accountsPayableLedger").contains("accountsReceivableLedger");
        assertThat(entries.keySet()).contains("01_PDF_Klaus_Furniere.pdf", "16_AR_Laub.pdf",
                "18_Sonstige_Mietvertrag.pdf");
    }

    @Test
    void everyLedgerFileInThePackageIsSchemaValid() {
        List.of("01_PDF_Klaus_Furniere.pdf", "13_XRechnung_Spanexempel_gemischt.xml", "16_AR_Laub.pdf")
                .forEach(beleg -> review.submit(Belege.document(beleg)));

        Map<String, byte[]> entries = entriesOf(export.exportPending().content());

        assertThat(entries.keySet().stream().filter(name -> name.startsWith("Rechnungsdaten_")).toList())
                .hasSize(3)
                .allSatisfy(name -> DatevSchema.assertValid(DatevSchema.Kind.LEDGER, text(entries, name)));
    }

    @Test
    void keepsDocumentNamesUniqueWhenTheSameFileArrivesTwice() {
        review.submit(Belege.document("01_PDF_Klaus_Furniere.pdf"));
        review.submit(Belege.document("01_PDF_Klaus_Furniere.pdf"));

        Map<String, byte[]> entries = entriesOf(export.exportPending().content());

        assertThat(entries.keySet().stream().filter(name -> name.endsWith(".pdf")).toList()).hasSize(2);
    }

    @Test
    void thePackageNameCarriesTheCreationDate() {
        review.submit(Belege.document("01_PDF_Klaus_Furniere.pdf"));

        assertThat(export.exportPending().filename())
                .matches("DATEV_Belege_\\d{8}_\\d{6}\\.zip");
    }
}
