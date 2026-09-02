package de.pegasus.invoicematcher.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Das Postfach-Passwort landet verschluesselt in der SQLite-Datei - hier wird nur der
 * Krypto-Baustein selbst geprueft, nicht die Ablage.
 */
@DisplayName("SecretCipher")
class SecretCipherTest {

    private static String zufallsSchluessel() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private SecretCipher cipher() {
        return new SecretCipher(new SettingsKeyProperties(zufallsSchluessel()));
    }

    @Test
    void encryptedTextDecryptsBackToTheOriginal() {
        SecretCipher cipher = cipher();

        String verschluesselt = cipher.encrypt("geheimes-postfach-passwort");

        assertThat(cipher.decrypt(verschluesselt)).isEqualTo("geheimes-postfach-passwort");
    }

    @Test
    void theSameInputEncryptsDifferentlyEachTime() {
        SecretCipher cipher = cipher();

        String erstesMal = cipher.encrypt("dasselbe-passwort");
        String zweitesMal = cipher.encrypt("dasselbe-passwort");

        assertThat(erstesMal).isNotEqualTo(zweitesMal);
        assertThat(cipher.decrypt(erstesMal)).isEqualTo("dasselbe-passwort");
        assertThat(cipher.decrypt(zweitesMal)).isEqualTo("dasselbe-passwort");
    }

    @Test
    void decryptingWithTheWrongKeyFailsInsteadOfReturningGarbage() {
        String verschluesselt = cipher().encrypt("geheimes-postfach-passwort");
        SecretCipher andererSchluessel = cipher();

        assertThatThrownBy(() -> andererSchluessel.decrypt(verschluesselt))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aKeyOfTheWrongLengthIsRejectedAtConstruction() {
        String zuKurz = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new SecretCipher(new SettingsKeyProperties(zuKurz)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 Byte");
    }
}
