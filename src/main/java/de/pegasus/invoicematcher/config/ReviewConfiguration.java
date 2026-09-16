package de.pegasus.invoicematcher.config;

import de.pegasus.invoicematcher.review.ReviewService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** Der Hintergrundpfad der Belegannahme. */
@Configuration
public class ReviewConfiguration {

    /**
     * Klassifiziert angenommene Belege hinter der Annahme her.
     *
     * <p>Bewusst schmal: zwei Faeden reichen, weil jede Klassifikation ohnehin an zwei
     * KI-Anfragen haengt - mehr Parallelitaet liefe nur schneller in die Kontingentgrenzen des
     * Anbieters. Die Warteschlange ist begrenzt und laeuft im Notfall auf dem einreichenden
     * Faden weiter ({@code CallerRunsPolicy}, der Vorgabewert von Spring): lieber wartet eine
     * Annahme, als dass ein angenommener Beleg unklassifiziert liegen bleibt.
     */
    @Bean(name = ReviewService.BELEGVERARBEITUNG)
    public Executor belegVerarbeitung() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("beleg-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
