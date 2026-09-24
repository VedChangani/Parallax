package in.vedchangani.parallax.backend;

import in.vedchangani.parallax.engine.Backtester;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the framework-independent engine's stateless {@link Backtester}
 * as a Spring bean. This is the only point of contact between Spring
 * configuration and the engine module in D-29 — no engine class is
 * modified, and no Spring/JPA/persistence type is passed into the engine.
 */
@Configuration
public class EngineConfiguration {

    @Bean
    public Backtester backtester() {
        return new Backtester();
    }
}
