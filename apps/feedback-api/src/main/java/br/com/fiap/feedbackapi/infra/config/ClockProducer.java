package br.com.fiap.feedbackapi.infra.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

@ApplicationScoped
public class ClockProducer {
    @Produces
    Clock clock() {
        return Clock.systemUTC();
    }
}
