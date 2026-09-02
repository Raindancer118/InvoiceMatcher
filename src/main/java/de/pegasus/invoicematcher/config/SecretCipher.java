package de.pegasus.invoicematcher.config;

import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Verschluesselt Geheimnisse (aktuell nur das Postfach-Passwort der Belegannahme) fuer die
 * Ablage in SQLite. AES-256-GCM ueber die JDK-eigene {@code javax.crypto} - eine neue
 * Abhaengigkeit waere fuer diesen einen Anwendungsfall Ballast.
 *
 * <p>IV, Ciphertext und Authentifizierungs-Tag liegen in einem einzigen Base64-String
 * (IV + Ciphertext, das Tag steckt bereits im GCM-Ciphertext): kein zweites Feld je Geheimnis
 * in der Datenbank noetig.
 */
@Component
public class SecretCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LAENGE_BYTES = 12;
    private static final int TAG_LAENGE_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom zufall = new SecureRandom();

    public SecretCipher(SettingsKeyProperties keyProperties) {
        byte[] rohschluessel = Base64.getDecoder().decode(keyProperties.key());
        if (rohschluessel.length != 32) {
            throw new IllegalStateException("invoicematcher.settings.key muss 32 Byte (Base64) lang sein"
                    + " - openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(rohschluessel, "AES");
    }

    public String encrypt(String klartext) {
        try {
            byte[] iv = new byte[IV_LAENGE_BYTES];
            zufall.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LAENGE_BITS, iv));
            byte[] ciphertext = cipher.doFinal(klartext.getBytes(StandardCharsets.UTF_8));
            byte[] kombiniert = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, kombiniert, 0, iv.length);
            System.arraycopy(ciphertext, 0, kombiniert, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(kombiniert);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Verschluesselung fehlgeschlagen", e);
        }
    }

    public String decrypt(String verschluesselt) {
        try {
            byte[] kombiniert = Base64.getDecoder().decode(verschluesselt);
            byte[] iv = Arrays.copyOfRange(kombiniert, 0, IV_LAENGE_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(kombiniert, IV_LAENGE_BYTES, kombiniert.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LAENGE_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("Entschluesselung fehlgeschlagen - falscher Schluessel"
                    + " oder beschaedigte Daten", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Entschluesselung fehlgeschlagen", e);
        }
    }
}
