package de.pegasus.invoicematcher.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Ablehnung eines Pruefvorgangs.
 *
 * @param benutzer wer ablehnt
 * @param grund    warum - ohne Begruendung ist eine Ablehnung nicht pruefbar
 */
public record RejectRequest(@NotBlank String benutzer, @NotBlank String grund) {
}
