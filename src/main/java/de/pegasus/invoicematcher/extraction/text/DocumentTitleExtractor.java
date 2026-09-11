package de.pegasus.invoicematcher.extraction.text;

import java.util.Optional;

/**
 * Bestimmt die Belegueberschrift - die Grundlage von Regel 1.1.
 *
 * <p>Gesucht wird in der linken Spalte der ersten Belegzeilen nach einer bekannten Belegart
 * ({@link DocumentKindPatterns}).
 */
public final class DocumentTitleExtractor implements TextFieldExtractor<String> {

    /** Zeilen, in denen eine Ueberschrift ueberhaupt stehen kann. */
    private static final int HEADER_LINES = 30;

    @Override
    public Optional<String> extract(DocumentText text) {
        int limit = Math.min(HEADER_LINES, text.lines().size());
        for (int i = 0; i < limit; i++) {
            String candidate = text.firstColumnOf(i);
            if (DocumentKindPatterns.matches(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
