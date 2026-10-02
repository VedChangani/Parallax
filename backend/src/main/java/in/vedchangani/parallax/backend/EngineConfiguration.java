package in.vedchangani.parallax.backend;

import in.vedchangani.parallax.engine.Backtester;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfiguration {

    @Bean
    public Backtester backtester() {
        return new Backtester();
    }
}
