package de.pegasus.invoicematcher.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Die Freigabeschwelle, wie sie aus der Oberflaeche kommt.
 *
 * @param freigabeschwelle Konfidenz zwischen 0 und 1, ab der ein Beleg ohne menschliche
 *                         Pruefung durchlaeuft
 */
public record PruefungRequest(@NotNull @DecimalMin("0.0") @DecimalMax("1.0")
                              BigDecimal freigabeschwelle) {
}
