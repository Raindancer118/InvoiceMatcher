package de.pegasus.invoicematcher.export;

import java.util.List;

/**
 * Ein fertiges DATEV Document-Package.
 *
 * @param filename  Dateiname des ZIP-Archivs
 * @param content   Inhalt des Archivs
 * @param caseIds   die enthaltenen Pruefvorgaenge
 */
public record DatevPackage(String filename, byte[] content, List<String> caseIds) {

    public DatevPackage {
        caseIds = List.copyOf(caseIds);
    }
}
