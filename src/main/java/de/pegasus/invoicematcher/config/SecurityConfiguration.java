package de.pegasus.invoicematcher.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;


/**
 * Absicherung der REST-Schnittstelle gegen Authentik.
 *
 * <p>Zustandslos: die Sitzung haelt die Oberflaeche, die API bekommt bei jedem Aufruf ein
 * Zugriffstoken und prueft es gegen den Herausgeber. Damit gibt es serverseitig nichts, was
 * ablaufen oder zwischen zwei Instanzen abgeglichen werden muesste.
 *
 * <p>Ist kein Herausgeber konfiguriert, laeuft die Anwendung ohne Anmeldung weiter und sagt das
 * beim Start deutlich. Der stille Zwischenzustand - Sicherheitsschicht vorhanden, aber wirkungslos -
 * ist die gefaehrlichere Variante.
 */
@Configuration
public class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    /** Der Healthcheck des Containers kann sich nicht anmelden. */
    private static final String[] OEFFENTLICH = {"/actuator/health", "/actuator/health/**", "/actuator/info"};

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecurityProperties properties) throws Exception {
        // withDefaults() greift gezielt die Bean 'corsConfigurationSource' ab; ein Parameter des
        // Typs waere mehrdeutig, weil der MVC-Introspector denselben Typ mitbringt.
        http.cors(Customizer.withDefaults())
                // Kein Cookie, keine Sitzung, kein CSRF-Angriffsweg: die API kennt nur Bearer-Token.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!properties.isEnabled()) {
            log.warn("Kein OIDC-Herausgeber konfiguriert - die API ist OHNE ANMELDUNG erreichbar. "
                    + "Fuer den Betrieb 'invoicematcher.security.issuer-uri' setzen.");
            return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
        }

        log.info("API abgesichert gegen {}{}", properties.issuerUri(),
                properties.requiredGroup() == null ? "" : ", verlangte Gruppe: " + properties.requiredGroup());

        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(OEFFENTLICH).permitAll()
                        .anyRequest().access(zugangsregel(properties)))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter(properties))))
                // 401 statt einer Weiterleitung auf ein Anmeldeformular: hier spricht nur die Oberflaeche.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    private static AuthorizationManager<RequestAuthorizationContext> zugangsregel(SecurityProperties properties) {
        return properties.requiredGroup() == null
                ? AuthorityAuthorizationManager.hasAuthority("SCOPE_openid")
                : AuthorityAuthorizationManager.hasAuthority(TokenRechte.fuerGruppe(properties.requiredGroup()));
    }

    /**
     * Authentik liefert die Gruppen als Liste im Token. Ein Konto ohne die verlangte Gruppe hat
     * ein gueltiges Token und trotzdem hier nichts zu suchen.
     */
    @Bean
    public JwtAuthenticationConverter authenticationConverter(SecurityProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(token -> TokenRechte.aus(token, properties.groupsClaim()));
        return converter;
    }

    /**
     * Wird nur gebaut, wenn ein Herausgeber eingetragen ist - der Aufbau fragt dessen
     * Konfiguration ueber das Netz ab und wuerde ohne Adresse den Start abbrechen.
     */
    @Bean
    @Conditional(OidcEingerichtet.class)
    public JwtDecoder jwtDecoder(SecurityProperties properties) {
        return JwtDecoders.fromIssuerLocation(properties.issuerUri());
    }

    /**
     * Eine leere Angabe zaehlt als nicht eingerichtet. {@code @ConditionalOnProperty} reicht
     * dafuer nicht: die Eigenschaft steht in der YAML und ist ohne gesetzte Umgebungsvariable
     * lediglich leer, aus Sicht der Bedingung also vorhanden.
     */
    static class OidcEingerichtet implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String issuer = context.getEnvironment().getProperty("invoicematcher.security.issuer-uri");
            return issuer != null && !issuer.isBlank();
        }
    }
}
