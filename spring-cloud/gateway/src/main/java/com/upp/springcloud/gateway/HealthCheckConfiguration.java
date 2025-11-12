package com.upp.springcloud.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthContributor;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class HealthCheckConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(HealthCheckConfiguration.class);

    private final WebClient.Builder webClientBuilder;
    private WebClient webClient;

    @Autowired
    public HealthCheckConfiguration(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Bean
    ReactiveHealthContributor authServerHealthContributor() {
        return (ReactiveHealthIndicator) () -> getHealth("http://auth-server");
    }

    @Bean
    ReactiveHealthContributor productHealthContributor() {
        return (ReactiveHealthIndicator) () -> getHealth("http://product");
    }

    @Bean
    ReactiveHealthContributor recommendationHealthContributor() {
        return (ReactiveHealthIndicator) () -> getHealth("http://recommendation");
    }

    @Bean
    ReactiveHealthContributor reviewHealthContributor() {
        return (ReactiveHealthIndicator) () -> getHealth("http://review");
    }

    @Bean
    ReactiveHealthContributor productCompositeHealthContributor() {
        return (ReactiveHealthIndicator) () -> getHealth("http://product-composite");
    }

    private Mono<Health> getHealth(String url) {
        url += "/actuator/health";
        LOG.debug("Will call the Health API on URL: {}", url);
        return getWebClient().get().uri(url).retrieve().bodyToMono(String.class)
                .map(s -> Health.up().build())
                .onErrorResume(ex -> Mono.just(Health.down().withException((Exception) ex).build()))
                .log();
    }

    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = webClientBuilder.build();
        }
        return webClient;
    }
}
