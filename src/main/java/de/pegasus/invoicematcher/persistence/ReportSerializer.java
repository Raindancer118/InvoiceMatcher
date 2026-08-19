package de.pegasus.invoicematcher.persistence;

import de.pegasus.invoicematcher.classification.ClassificationReport;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Legt das Klassifikationsergebnis als JSON ab.
 *
 * <p>Bewusst als Ganzes und nicht in Spalten zerlegt: der Bericht ist ein Wertobjekt, das
 * unveraendert wieder herauskommen muss - inklusive Findings und Belegkontext. Er wird nie
 * relational abgefragt, sondern immer vollstaendig zu seinem Vorgang geladen.
 *
 * <p>Die Ablage nutzt bewusst einen eigenen Mapper und nicht den der HTTP-Schnittstelle. Sonst
 * wuerde eine Anpassung der Darstellung fuer das Frontend - etwa ein anderes Datumsformat -
 * bereits abgelegte Vorgaenge unlesbar machen.
 */
@Component
public class ReportSerializer {

    private final ObjectMapper storageMapper = JsonMapper.builder().build();

    public String toJson(ClassificationReport report) {
        try {
            return storageMapper.writeValueAsString(report);
        } catch (JacksonException e) {
            throw new IllegalStateException("Klassifikationsergebnis konnte nicht abgelegt werden", e);
        }
    }

    public ClassificationReport fromJson(String json) {
        try {
            return storageMapper.readValue(json, ClassificationReport.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Abgelegtes Klassifikationsergebnis ist nicht lesbar", e);
        }
    }
}
