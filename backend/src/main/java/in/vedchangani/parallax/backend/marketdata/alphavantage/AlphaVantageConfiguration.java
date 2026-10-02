package in.vedchangani.parallax.backend.marketdata.alphavantage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AlphaVantageProperties.class)
public class AlphaVantageConfiguration {

    @Bean
    public AlphaVantageMarketDataProvider alphaVantageMarketDataProvider(AlphaVantageProperties properties) {
        return new AlphaVantageMarketDataProvider(properties);
    }
}
