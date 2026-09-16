package de.pegasus.invoicematcher.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Firmendaten und Mail-Eingang ueber die REST-Schnittstelle - vorher nur per .env und Neustart
 * aenderbar.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Einstellungen-API")
class EinstellungenApiIntegrationTest {

    @org.junit.jupiter.api.io.TempDir
    static Path ablage;

    @DynamicPropertySource
    static void ablageEinrichten(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + ablage.resolve("test.db"));
    }

    private final MockMvc mockMvc;

    @Autowired
    EinstellungenApiIntegrationTest(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void firmaStartsWithTheSeededDefaultsFromApplicationYml() throws Exception {
        mockMvc.perform(get("/api/v1/einstellungen/firma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Muster zu Testzwecken GmbH"));
    }

    @Test
    void savedCompanySettingsAreReturnedOnTheNextRead() throws Exception {
        mockMvc.perform(put("/api/v1/einstellungen/firma")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Echtes Unternehmen GmbH", "ustIdNr": "DE123456789", "steuernummer": "1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Echtes Unternehmen GmbH"));

        mockMvc.perform(get("/api/v1/einstellungen/firma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Echtes Unternehmen GmbH"))
                .andExpect(jsonPath("$.ustIdNr").value("DE123456789"));
    }

    @Test
    void aBlankCompanyNameIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/einstellungen/firma")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mailIntakeSettingsNeverExposeThePasswordItself() throws Exception {
        mockMvc.perform(put("/api/v1/einstellungen/mail-eingang")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aktiv": true, "protokoll": "imaps", "host": "mx.example",
                                  "port": 993, "benutzername": "belege@example.de",
                                  "passwort": "geheimes-passwort",
                                  "ordner": "INBOX", "verarbeitetOrdner": "Verarbeitet",
                                  "fehlgeschlagenOrdner": "Fehlgeschlagen", "taktSekunden": 300,
                                  "maxNachrichtenProDurchlauf": 50, "maxAnhangGroesseKb": 20000,
                                  "erlaubteAbsender": ["@example.de"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwortGesetzt").value(true))
                .andExpect(jsonPath("$..passwort").doesNotExist());

        mockMvc.perform(get("/api/v1/einstellungen/mail-eingang"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwortGesetzt").value(true))
                .andExpect(jsonPath("$.host").value("mx.example"))
                .andExpect(jsonPath("$..passwort").doesNotExist());
    }

    @Test
    void anEmptyPasswordOnUpdateKeepsTheExistingOne() throws Exception {
        String mitPasswort = """
                {
                  "aktiv": true, "protokoll": "imaps", "host": "mx.example",
                  "port": 993, "benutzername": "belege@example.de",
                  "passwort": "erstes-passwort",
                  "ordner": "INBOX", "verarbeitetOrdner": "Verarbeitet",
                  "fehlgeschlagenOrdner": "Fehlgeschlagen", "taktSekunden": 300,
                  "maxNachrichtenProDurchlauf": 50, "maxAnhangGroesseKb": 20000,
                  "erlaubteAbsender": ["@example.de"]
                }
                """;
        mockMvc.perform(put("/api/v1/einstellungen/mail-eingang")
                        .contentType(MediaType.APPLICATION_JSON).content(mitPasswort))
                .andExpect(status().isOk());

        String ohnePasswort = mitPasswort.replace("\"passwort\": \"erstes-passwort\",", "\"passwort\": \"\",");
        mockMvc.perform(put("/api/v1/einstellungen/mail-eingang")
                        .contentType(MediaType.APPLICATION_JSON).content(ohnePasswort))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwortGesetzt").value(true));
    }

    @Test
    void anInvalidPortIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/einstellungen/mail-eingang")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aktiv": false, "protokoll": "imaps", "host": "mx.example",
                                  "port": 70000, "benutzername": "belege@example.de",
                                  "ordner": "INBOX", "verarbeitetOrdner": "Verarbeitet",
                                  "fehlgeschlagenOrdner": "Fehlgeschlagen", "taktSekunden": 300,
                                  "maxNachrichtenProDurchlauf": 50, "maxAnhangGroesseKb": 20000
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void kontenrahmenStartsWithTheSeededDefaultsAndIsEditableAsRawJson() throws Exception {
        mockMvc.perform(get("/api/v1/einstellungen/kontenrahmen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.json").value(org.hamcrest.Matchers.containsString("Furniere/Kanten")));

        mockMvc.perform(put("/api/v1/einstellungen/kontenrahmen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"json": "{\\"gwgLimit\\":1200.00,\\"roundingTolerance\\":0.01,\\"revenue\\":[],\\"defaultExpense\\":[],\\"categories\\":[],\\"fixedAssetKeywords\\":[]}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.json").value(org.hamcrest.Matchers.containsString("1200.0")));

        mockMvc.perform(get("/api/v1/einstellungen/kontenrahmen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.json").value(org.hamcrest.Matchers.containsString("1200.0")));
    }

    @Test
    void malformedKontenrahmenJsonIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/einstellungen/kontenrahmen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"json\": \"das ist kein JSON\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void geschaeftspartnerStartsWithTheSeededDefaultsAndIsEditableAsRawJson() throws Exception {
        mockMvc.perform(get("/api/v1/einstellungen/geschaeftspartner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.json").value(org.hamcrest.Matchers.containsString("Friedrich Klausmuster")));

        mockMvc.perform(put("/api/v1/einstellungen/geschaeftspartner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"json": "{\\"diverse\\":{\\"creditorStart\\":70001,\\"debitorStart\\":10001},\\"known\\":[{\\"konto\\":70201,\\"name\\":\\"Neuer Partner\\",\\"direction\\":\\"EINGANG\\",\\"vatId\\":null,\\"taxNumber\\":null}]}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.json").value(org.hamcrest.Matchers.containsString("Neuer Partner")));
    }

    @Test
    void theApprovalThresholdCanBeReadAndChanged() throws Exception {
        mockMvc.perform(get("/api/v1/einstellungen/pruefung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freigabeschwelle").value(0.85));

        mockMvc.perform(put("/api/v1/einstellungen/pruefung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"freigabeschwelle\": 0.7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freigabeschwelle").value(0.7));

        mockMvc.perform(get("/api/v1/einstellungen/pruefung"))
                .andExpect(jsonPath("$.freigabeschwelle").value(0.7));
    }

    /** Eine Konfidenz groesser 1 gibt es nicht - das gehoert abgewiesen, nicht gespeichert. */
    @Test
    void refusesAThresholdOutsideTheConfidenceRange() throws Exception {
        mockMvc.perform(put("/api/v1/einstellungen/pruefung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"freigabeschwelle\": 1.5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void kiSettingsNeverExposeTheStoredKeysThemselves() throws Exception {
        mockMvc.perform(put("/api/v1/einstellungen/ki")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fastModell": "llama-test", "fastSchluessel": ["geheimer-schluessel"],
                                 "smartModell": "gemini-test", "smartSchluessel": []}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fastModell").value("llama-test"))
                .andExpect(jsonPath("$.fastSchluesselGesetzt").value(true))
                .andExpect(jsonPath("$..fastSchluessel").doesNotExist());

        mockMvc.perform(get("/api/v1/einstellungen/ki"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fastModell").value("llama-test"))
                .andExpect(jsonPath("$.fastSchluesselGesetzt").value(true));
    }

    @Test
    void keyStatusListsEveryStoredKeyMaskedAndWithoutCallingAnyProvider() throws Exception {
        mockMvc.perform(put("/api/v1/einstellungen/ki")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fastModell": "llama-test", "fastSchluessel": ["gsk_geheim_abcd", "gsk_zweiter_efgh"],
                                 "smartModell": "gemini-test", "smartSchluessel": ["AIzaGeheim1234"]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/einstellungen/ki/schluessel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].stufe").value("fast"))
                .andExpect(jsonPath("$[0].anbieter").value("groq"))
                .andExpect(jsonPath("$[0].schluessel").value("gsk_…abcd"))
                .andExpect(jsonPath("$[0].verfuegbar").value(true))
                .andExpect(jsonPath("$[0].probe").doesNotExist())
                .andExpect(jsonPath("$[2].stufe").value("smart"))
                .andExpect(jsonPath("$[2].schluessel").value("AIza…1234"));
    }

    @Test
    void kiConnectionTestReportsFailureWithoutAKey() throws Exception {
        mockMvc.perform(post("/api/v1/einstellungen/ki/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fastModell": "llama-test", "fastSchluessel": [],
                                 "smartModell": "gemini-test", "smartSchluessel": []}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fastOk").value(false))
                .andExpect(jsonPath("$.smartOk").value(false));
    }
}
