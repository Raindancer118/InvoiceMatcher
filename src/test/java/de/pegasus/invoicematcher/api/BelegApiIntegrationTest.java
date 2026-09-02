package de.pegasus.invoicematcher.api;

import de.pegasus.invoicematcher.support.Belege;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Startet den vollstaendigen Anwendungskontext und fuehrt einen echten Beleg durch die API.
 *
 * <p>Damit ist zugleich der Startup-Test abgedeckt: faellt eine Bean-Verdrahtung oder die
 * Konfigurationsbindung aus, schlaegt dieser Test fehl, nicht erst der Betrieb.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Beleg-API")
class BelegApiIntegrationTest {

    /** Eigene Ablage je Testlauf, damit der Test nichts im Arbeitsverzeichnis hinterlaesst. */
    @org.junit.jupiter.api.io.TempDir
    static java.nio.file.Path ablage;

    @org.springframework.test.context.DynamicPropertySource
    static void ablageEinrichten(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + ablage.resolve("test.db"));
    }

    private final MockMvc mockMvc;

    @Autowired
    BelegApiIntegrationTest(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static MockMultipartFile upload(String filename) {
        return new MockMultipartFile("datei", filename, MediaType.APPLICATION_PDF_VALUE, Belege.bytes(filename));
    }

    @Test
    void classifiesAnUploadedInvoiceAndReturnsTheDatevIntermediateFormat() throws Exception {
        mockMvc.perform(multipart("/api/v1/belege/klassifikation")
                        .file(upload("01_PDF_Klaus_Furniere.pdf")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ergebnis.document_type").value("Rechnungseingang"))
                .andExpect(jsonPath("$.ergebnis.belegdatum").value("04012026"))
                .andExpect(jsonPath("$.ergebnis.belegfeld_1").value("5007"))
                .andExpect(jsonPath("$.ergebnis.personenkonto").value(70101))
                .andExpect(jsonPath("$.ergebnis.buchungszeilen[0].sachkonto").value(3401))
                .andExpect(jsonPath("$.ergebnis.buchungszeilen[0].soll_haben_kennzeichen").value("S"))
                .andExpect(jsonPath("$.ergebnis.konfidenz_score").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.quelle.format").value("PDF"));
    }

    @Test
    void deliversTheOriginalDocumentSoTheReviewerCanCompareItWithTheProposal() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(multipart("/api/v1/belege").file(upload("05_PDF_Buerobedarf_Kopierer.pdf")))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id");

        byte[] ausgeliefert = mockMvc.perform(get("/api/v1/pruefungen/" + id + "/beleg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                // inline, nicht attachment: das Belegbild wird im Pruefblick angezeigt, nicht geladen
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("inline")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(ausgeliefert).isEqualTo(Belege.bytes("05_PDF_Buerobedarf_Kopierer.pdf"));
    }

    @Test
    void reportsAnUnknownCaseInsteadOfAnEmptyDocument() throws Exception {
        mockMvc.perform(get("/api/v1/pruefungen/gibtesnicht/beleg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void submittingAFlaggedDocumentOpensAReviewCaseThatCanThenBeApproved() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(multipart("/api/v1/belege").file(upload("05_PDF_Buerobedarf_Kopierer.pdf")))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("OFFEN"))
                        .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/pruefungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").exists());

        // Ein buchbarer Belegtyp ohne Buchungszeilen wird abgelehnt - sonst bliebe der Beleg
        // stillschweigend ungebucht.
        mockMvc.perform(post("/api/v1/pruefungen/" + id + "/freigabe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"benutzer\":\"tom\",\"belegtyp\":\"Rechnungseingang\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/pruefungen/" + id + "/freigabe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "benutzer": "tom",
                                  "belegtyp": "Rechnungseingang",
                                  "buchungszeilen": [
                                    {
                                      "sachkonto": 4930,
                                      "soll_haben_kennzeichen": "S",
                                      "steuerschluessel": 9,
                                      "umsatz": 899.99,
                                      "buchungstext": "Digitalkopiergeraet HS-1233"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FREIGEGEBEN"))
                .andExpect(jsonPath("$.ergebnis.document_type").value("Rechnungseingang"))
                .andExpect(jsonPath("$.ergebnis.buchungszeilen[0].sachkonto").value(4930));
    }

    @Test
    void rejectsAnUnsupportedFileTypeWithAProblemDetail() throws Exception {
        mockMvc.perform(multipart("/api/v1/belege/klassifikation")
                        .file(new MockMultipartFile("datei", "notiz.txt", MediaType.TEXT_PLAIN_VALUE, "hallo".getBytes())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Beleg nicht lesbar"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void rejectsAnEmptyUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/belege/klassifikation")
                        .file(new MockMultipartFile("datei", "leer.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[0])))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAnUnknownReviewCaseAsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/pruefungen/gibt-es-nicht/freigabe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"benutzer\":\"tom\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void handsOutADatevDocumentPackageForApprovedDocuments() throws Exception {
        mockMvc.perform(multipart("/api/v1/belege").file(upload("09_ZUGFeRD_Unkastester_Furniere.pdf")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTOMATISCH_FREIGEGEBEN"));

        mockMvc.perform(get("/api/v1/export/offen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.dateiname == '09_ZUGFeRD_Unkastester_Furniere.pdf')]").exists());

        byte[] zip = mockMvc.perform(post("/api/v1/export/datev"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("DATEV_Belege_")))
                .andReturn().getResponse().getContentAsByteArray();

        java.util.List<String> namen = new java.util.ArrayList<>();
        try (java.util.zip.ZipInputStream in =
                     new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            for (java.util.zip.ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                namen.add(entry.getName());
            }
        }
        assertThat(namen).contains("document.xml", "09_ZUGFeRD_Unkastester_Furniere.pdf");
        assertThat(namen).anyMatch(name -> name.startsWith("Rechnungsdaten_"));
    }

    @Test
    void reportsAnEmptyExportAsAConflictInsteadOfAnEmptyArchive() throws Exception {
        // Andere Testfaelle teilen sich den Anwendungskontext; erst leeren, dann pruefen.
        drainPendingExports();

        mockMvc.perform(post("/api/v1/export/datev"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Nichts zu exportieren"));
    }

    private void drainPendingExports() throws Exception {
        while (mockMvc.perform(post("/api/v1/export/datev")).andReturn().getResponse().getStatus() == 200) {
            // weiter, bis kein freigegebener Beleg mehr offen ist
        }
    }

    @Test
    void publishesTheRuleSetAndMasterDataForTheFrontend() throws Exception {
        mockMvc.perform(get("/api/v1/stammdaten/regeln"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1.1"));

        mockMvc.perform(get("/api/v1/stammdaten/partner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.konto == 70101)].name").value("Friedrich Klausmuster"));

        mockMvc.perform(get("/api/v1/stammdaten/kategorien"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.account == 3401)].label").value("Furniere/Kanten"));
    }
}
