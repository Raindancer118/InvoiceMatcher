package de.pegasus.invoicematcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Angaben, die das DATEV Document-Package traegt, aber nicht aus dem Beleg stammen.
 *
 * @param generatingSystem  Name des erzeugenden Systems im Archiv-Kopf
 * @param consultantNumber  Beraternummer, falls bekannt
 * @param clientNumber      Mandantennummer, falls bekannt
 * @param incomingFolder    Bezeichnung des Rechnungseingangsordners in Belege online
 * @param outgoingFolder    Bezeichnung des Rechnungsausgangsordners
 * @param xmlDataNotice     Pflichthinweis im Attribut xml_data der Belegsatzdatendatei
 */
@ConfigurationProperties("invoicematcher.datev")
public record DatevExportProperties(String generatingSystem,
                                    String consultantNumber,
                                    String clientNumber,
                                    String clientName,
                                    String incomingFolder,
                                    String outgoingFolder,
                                    String xmlDataNotice) {
}
