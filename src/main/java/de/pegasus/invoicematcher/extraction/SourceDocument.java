package de.pegasus.invoicematcher.extraction;

import java.util.Locale;

/**
 * Ein eingegangener Beleg, so wie er das System erreicht - Dateiname plus Inhalt.
 *
 * <p>Der Dateiname ist nur ein Hinweis; die Formaterkennung stuetzt sich zusaetzlich auf den
 * Inhalt, weil Belege aus E-Mail-Anhaengen haeufig unzuverlaessig benannt sind.
 */
public record SourceDocument(String filename, byte[] content) {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    public SourceDocument {
        if (content == null) {
            throw new IllegalArgumentException("Beleginhalt darf nicht null sein");
        }
        filename = filename == null || filename.isBlank() ? "unbenannt" : filename;
    }

    public boolean isPdf() {
        return startsWith(PDF_MAGIC) || hasExtension("pdf");
    }

    public boolean isXml() {
        return hasExtension("xml") || startsWithXmlDeclaration();
    }

    public boolean hasExtension(String extension) {
        return filename.toLowerCase(Locale.ROOT).endsWith("." + extension);
    }

    private boolean startsWith(byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWithXmlDeclaration() {
        int limit = Math.min(content.length, 64);
        String head = new String(content, 0, limit, java.nio.charset.StandardCharsets.UTF_8).stripLeading();
        return head.startsWith("<?xml") || head.startsWith("<");
    }
}
