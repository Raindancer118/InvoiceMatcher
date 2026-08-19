package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.masterdata.partner.PartyNames;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Das eigene Unternehmen.
 *
 * <p>Ohne diese Angabe laesst sich die Belegrichtung nicht bestimmen: dieselbe Rechnung ist
 * je nach Blickrichtung Eingang oder Ausgang.
 */
@ConfigurationProperties("invoicematcher.company")
public record CompanyProperties(String name, String vatId, String taxNumber) {

    /** Ab dieser Namensaehnlichkeit gilt ein Beteiligter als das eigene Unternehmen. */
    private static final double MATCH_THRESHOLD = 0.85;

    public boolean matches(de.pegasus.invoicematcher.domain.Party party) {
        if (party == null) {
            return false;
        }
        return sameIdentifier(party.vatId(), vatId)
                || sameIdentifier(party.taxNumber(), taxNumber)
                || PartyNames.similarity(party.name(), name) >= MATCH_THRESHOLD;
    }

    private static boolean sameIdentifier(String left, String right) {
        return left != null && right != null
                && left.replaceAll("\\s", "").equalsIgnoreCase(right.replaceAll("\\s", ""));
    }
}
