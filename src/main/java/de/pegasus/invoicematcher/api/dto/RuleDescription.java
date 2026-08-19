package de.pegasus.invoicematcher.api.dto;

import de.pegasus.invoicematcher.rules.IRule;

/** Eine Regel des Regelwerks, wie sie in der Oberflaeche erklaert wird. */
public record RuleDescription(String id, String beschreibung, int reihenfolge) {

    public static RuleDescription of(IRule rule) {
        return new RuleDescription(rule.id(), rule.description(), rule.getOrder());
    }
}
