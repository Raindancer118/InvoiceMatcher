package de.pegasus.invoicematcher.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import de.pegasus.invoicematcher.config.TokenRechte;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueft, dass die API ohne gueltiges Token und ohne die verlangte Gruppe verschlossen bleibt.
 *
 * <p>Die Anmeldung sitzt in der Oberflaeche, die API liegt daneben im selben Netz. Griffe die
 * Pruefung hier nicht, waere die Anmeldung eine Zierde: ein direkter Aufruf der API kaeme an
 * ihr vorbei.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Absicherung der API")
class ApiSicherheitTest {

    private static final String GRUPPE = "InvoiceMatcher";

    @org.junit.jupiter.api.io.TempDir
    static Path ablage;

    @DynamicPropertySource
    static void konfiguration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + ablage.resolve("sicherheit.db"));
        registry.add("invoicematcher.security.issuer-uri", () -> "https://portal.tstieh.de/application/o/invoicematcher/");
        registry.add("invoicematcher.security.required-group", () -> GRUPPE);
    }

    /** Der Herausgeber wird im Test nicht abgefragt; geprueft wird die Zugangsregel dahinter. */
    @MockitoBean
    JwtDecoder jwtDecoder;

    private final MockMvc mockMvc;

    @Autowired
    ApiSicherheitTest(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void weistEinenAufrufOhneTokenAb() throws Exception {
        mockMvc.perform(get("/api/v1/pruefungen"))
                .andExpect(status().isUnauthorized());
    }

    /** Dieselbe Uebersetzung wie im Betrieb, damit der Test die echte Kette prueft. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor mitGruppen(String... gruppen) {
        return jwt()
                .jwt(token -> token.claim("groups", java.util.List.of(gruppen)))
                .authorities(token -> TokenRechte.aus(token, "groups"));
    }

    @Test
    void weistEinGueltigesTokenOhneDieVerlangteGruppeAb() throws Exception {
        mockMvc.perform(get("/api/v1/pruefungen").with(mitGruppen("User")))
                .andExpect(status().isForbidden());
    }

    @Test
    void laesstEinKontoDerVerlangtenGruppeDurch() throws Exception {
        mockMvc.perform(get("/api/v1/pruefungen").with(mitGruppen("User", GRUPPE)))
                .andExpect(status().isOk());
    }

    @Test
    void laesstDenHealthcheckOhneAnmeldungDurch() throws Exception {
        // Der Container prueft damit seinen eigenen Zustand und kann sich nicht anmelden.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
