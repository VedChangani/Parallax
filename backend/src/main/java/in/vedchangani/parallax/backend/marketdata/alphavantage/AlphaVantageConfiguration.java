package in.vedchangani.parallax.backend.marketdata.alphavantage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Minimum Spring wiring for D-33 Batch 2: binds {@link AlphaVantageProperties}
 * and exposes {@link AlphaVantageMarketDataProvider} as a bean. Not yet
 * connected to any consumer (D-33 Batch 2 scope) — see {@link
 * AlphaVantageMarketDataProvider}'s Javadoc.
 */
@Configuration
@EnableConfigurationProperties(AlphaVantageProperties.class)
public class AlphaVantageConfiguration {

    @Bean
    public AlphaVantageMarketDataProvider alphaVantageMarketDataProvider(AlphaVantageProperties properties) {
        return new AlphaVantageMarketDataProvider(properties);
    }
}
