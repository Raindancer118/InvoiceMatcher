package de.pegasus.invoicematcher.masterdata;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalisiert Text fuer Stichwortvergleiche.
 *
 * <p>Belege schreiben denselben Begriff mal mit Umlaut, mal aufgeloest - "Digitalkopiergerät"
 * und "Digitalkopiergeraet" meinen dasselbe Geraet. Stichwortlisten koennen dadurch in reinem
 * ASCII gepflegt werden und treffen trotzdem beide Schreibweisen.
 */
public final class MatchingText {

    private MatchingText() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String expanded = value.toLowerCase(Locale.GERMAN)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        return Normalizer.normalize(expanded, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Prueft, ob das Stichwort im Text vorkommt - beide zuvor normalisiert. */
    public static boolean contains(String text, String keyword) {
        String normalizedKeyword = normalize(keyword);
        return !normalizedKeyword.isEmpty() && normalize(text).contains(normalizedKeyword);
    }
}
