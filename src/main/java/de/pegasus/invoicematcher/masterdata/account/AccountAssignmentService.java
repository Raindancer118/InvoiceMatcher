package de.pegasus.invoicematcher.masterdata.account;

import de.pegasus.invoicematcher.domain.Direction;
import de.pegasus.invoicematcher.domain.InvoiceLine;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Wendet die Kontenstrategien in ihrer Reihenfolge an; die erste passende gewinnt. */
@Service
public class AccountAssignmentService {

    /** Notfallkontierung, falls selbst kein Sammelkonto konfiguriert ist. */
    private static final int UNASSIGNED_ACCOUNT = 3400;

    private final List<AccountResolver> resolvers;

    public AccountAssignmentService(List<AccountResolver> resolvers) {
        List<AccountResolver> ordered = new ArrayList<>(resolvers);
        ordered.sort(AnnotationAwareOrderComparator.INSTANCE);
        this.resolvers = List.copyOf(ordered);
    }

    public AccountAssignment assign(InvoiceLine line, Direction direction) {
        return resolvers.stream()
                .map(resolver -> resolver.resolve(line, direction))
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .orElseGet(() -> new AccountAssignment(UNASSIGNED_ACCOUNT, true, null, false,
                        "Keine Kontenstrategie konnte die Position zuordnen"));
    }
}
