package de.pegasus.invoicematcher.masterdata.account;

import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.InvoiceLine;
import org.springframework.core.Ordered;

import java.util.Optional;

/**
 * Bestimmt das Sachkonto einer Position.
 *
 * <p>Mehrere Strategien greifen nacheinander: erst die spezifische Warengruppe, zuletzt das
 * Sammelkonto. Eine neue Strategie - etwa eine lernende Zuordnung aus Vorjahresbuchungen -
 * wird als weitere Implementierung eingehaengt.
 */
public interface AccountResolver extends Ordered {

    Optional<AccountAssignment> resolve(InvoiceLine line, Direction direction);
}
