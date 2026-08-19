package de.pegasus.invoicematcher.masterdata.partner;

import de.pegasus.invoicematcher.masterdata.MatchingText;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalisierung und Aehnlichkeitsmass fuer Firmen- und Personennamen.
 *
 * <p>Belege schreiben denselben Partner selten identisch zur Stammdatenpflege: Rechtsformzusatz
 * mal mit, mal ohne, Umlaute je nach Schriftsatz aufgeloest, Interpunktion beliebig. Die
 * Normalisierung raeumt diese Unterschiede weg, bevor gemessen wird.
 */
public final class PartyNames {

    /** Rechtsformzusaetze tragen keine Unterscheidungskraft und werden entfernt. */
    private static final Set<String> LEGAL_FORMS = Set.of(
            "gmbh", "mbh", "ag", "kg", "ohg", "gbr", "ug", "se", "ek", "eg", "kgaa",
            "co", "cie", "ltd", "plc", "inc", "sa", "bv", "nv", "haftungsbeschraenkt");

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9 ]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final JaroWinklerSimilarity SIMILARITY = new JaroWinklerSimilarity();

    private PartyNames() {
    }

    /** Kleinschreibung, Umlaute aufgeloest, Interpunktion und Rechtsform entfernt. */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String cleaned = NON_ALPHANUMERIC.matcher(MatchingText.normalize(name)).replaceAll(" ");
        return WHITESPACE.splitAsStream(cleaned.strip())
                .filter(token -> !token.isEmpty() && !LEGAL_FORMS.contains(token))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    /** Aehnlichkeit zweier Namen zwischen 0.0 und 1.0, jeweils nach {@link #normalize(String)}. */
    public static double similarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        if (a.equals(b) || a.contains(b) || b.contains(a)) {
            return 1.0;
        }
        return SIMILARITY.apply(a, b);
    }
}
