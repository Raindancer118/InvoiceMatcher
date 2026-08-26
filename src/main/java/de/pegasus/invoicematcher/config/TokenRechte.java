package de.pegasus.invoicematcher.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Uebersetzt die Angaben eines Zugriffstokens in Rechte.
 *
 * <p>Eigene Klasse statt einer Methode in der Sicherheitskonfiguration, damit genau diese
 * Uebersetzung fuer sich geprueft werden kann: sie entscheidet, wer an die Belege kommt.
 */
public final class TokenRechte {

    /** Praefix der Gruppenrechte; haelt sie von Scopes auseinander. */
    public static final String GRUPPE = "GRUPPE_";

    private TokenRechte() {
    }

    public static String fuerGruppe(String gruppe) {
        return GRUPPE + gruppe;
    }

    /** Authentik liefert die Gruppen als Liste, die Scopes als eine durch Leerzeichen getrennte Zeile. */
    public static Collection<GrantedAuthority> aus(Jwt token, String groupsClaim) {
        List<GrantedAuthority> rechte = new ArrayList<>();
        if (token.getClaim(groupsClaim) instanceof Collection<?> gruppen) {
            gruppen.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(name -> !name.isBlank())
                    .map(name -> new SimpleGrantedAuthority(fuerGruppe(name)))
                    .forEach(rechte::add);
        }
        String scopes = token.getClaimAsString("scope");
        if (scopes != null) {
            for (String scope : scopes.split(" ")) {
                if (!scope.isBlank()) {
                    rechte.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                }
            }
        }
        return rechte;
    }
}
