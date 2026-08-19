package de.pegasus.invoicematcher.masterdata.partner;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import de.pegasus.invoicematcher.domain.Direction;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Liest einen DATEV-DTVF-Export der Debitoren-/Kreditorenstammdaten.
 *
 * <p>Die Belegrichtung steht nicht in der Datei, sondern folgt aus dem Nummernkreis:
 * Debitoren ab 10000, Kreditoren ab 70000.
 */
@Component
public class DtvfPartnerReader {

    /** DATEV exportiert in der Windows-Codepage, nicht in UTF-8. */
    private static final Charset DATEV_CHARSET = Charset.forName("windows-1252");

    private static final char SEPARATOR = ';';
    private static final int CREDITOR_RANGE_START = 70000;

    private static final int COLUMN_KONTO = 0;
    private static final int COLUMN_NAME = 1;
    private static final int COLUMN_VAT_ID = 3;
    private static final int COLUMN_TAX_NUMBER = 4;

    public List<BusinessPartner> read(byte[] csv) {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(csv), DATEV_CHARSET);
             CSVReader csvReader = new CSVReaderBuilder(reader)
                     .withCSVParser(new CSVParserBuilder().withSeparator(SEPARATOR).build())
                     .withSkipLines(1)
                     .build()) {
            List<BusinessPartner> partners = new ArrayList<>();
            for (String[] row : csvReader) {
                toPartner(row).ifPresent(partners::add);
            }
            return List.copyOf(partners);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new IllegalArgumentException("DTVF-Stammdaten konnten nicht gelesen werden: " + e.getMessage(), e);
        }
    }

    private java.util.Optional<BusinessPartner> toPartner(String[] row) {
        Integer konto = accountNumber(column(row, COLUMN_KONTO));
        String name = column(row, COLUMN_NAME);
        if (konto == null || name.isBlank()) {
            return java.util.Optional.empty();
        }
        Direction direction = konto >= CREDITOR_RANGE_START ? Direction.EINGANG : Direction.AUSGANG;
        return java.util.Optional.of(new BusinessPartner(konto, name,
                direction, blankToNull(column(row, COLUMN_VAT_ID)), blankToNull(column(row, COLUMN_TAX_NUMBER))));
    }

    private static Integer accountNumber(String value) {
        try {
            return value.isBlank() ? null : Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String column(String[] row, int index) {
        return index < row.length && row[index] != null ? row[index].strip() : "";
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value;
    }
}
