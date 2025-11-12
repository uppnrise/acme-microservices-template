package com.upp.microservices.composite.product.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upp.api.core.product.Product;
import com.upp.api.core.product.ProductService;
import com.upp.api.core.recommendation.Recommendation;
import com.upp.api.core.recommendation.RecommendationService;
import com.upp.api.core.review.Review;
import com.upp.api.core.review.ReviewService;
import com.upp.api.event.Event;
import com.upp.util.exceptions.InvalidInputException;
import com.upp.util.exceptions.NotFoundException;
import com.upp.util.http.HttpErrorInfo;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import static com.upp.api.event.Event.Type.CREATE;
import static com.upp.api.event.Event.Type.DELETE;
import static reactor.core.publisher.Flux.empty;

@Component
public class ProductCompositeIntegration implements ProductService, RecommendationService, ReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeIntegration.class);

    private static final String OUTPUT_PRODUCTS = "products-out-0";
    private static final String OUTPUT_RECOMMENDATIONS = "recommendations-out-0";
    private static final String OUTPUT_REVIEWS = "reviews-out-0";

    private final String productServiceUrl = "http://product";
    private final String recommendationServiceUrl = "http://recommendation";
    private final String reviewServiceUrl = "http://review";

    private final ObjectMapper mapper;
    private final WebClient.Builder webClientBuilder;
    private final StreamBridge streamBridge;

    private WebClient webClient;

    private final int productServiceTimeoutSec;

    @Autowired
    public ProductCompositeIntegration(
            WebClient.Builder webClientBuilder,
            ObjectMapper mapper,
            StreamBridge streamBridge,
            @Value("${app.product-service.timeoutSec}") int productServiceTimeoutSec
    ) {
        this.webClientBuilder = webClientBuilder;
        this.mapper = mapper;
        this.streamBridge = streamBridge;
        this.productServiceTimeoutSec = productServiceTimeoutSec;
    }

    @Retry(name = "product")
    @CircuitBreaker(name = "product")
    @Override
    public Mono<Product> getProduct(int productId, int delay, int faultPercent) {
        URI url = UriComponentsBuilder.fromUriString(productServiceUrl + "/product/{productId}?delay={delay}&faultPercent={faultPercent}").build(productId, delay, faultPercent);
        LOG.debug("Calling the getProduct API on URL: {}", url);

        return getWebClient().get()
                .uri(url)
                .retrieve()
                .bodyToMono(Product.class)
                .log()
                .onErrorMap(WebClientResponseException.class, ex -> handleException(ex))
                .timeout(Duration.ofSeconds(productServiceTimeoutSec));
    }

    @Override
    public Product createProduct(Product body) {
        sendMessage(OUTPUT_PRODUCTS, new Event(CREATE, body.getProductId(), body));
        return body;
    }

    @Override
    public void deleteProduct(int productId) {
        sendMessage(OUTPUT_PRODUCTS, new Event(DELETE, productId, null));
    }

    @Override
    public Flux<Recommendation> getRecommendations(int productId) {
        URI url = UriComponentsBuilder.fromUriString(recommendationServiceUrl + "/recommendation?productId={productId}").build(productId);
        LOG.debug("Calling the getRecommendations API on URL: {}", url);

        // Return an empty result if something goes wrong to make it possible for the composite service to return partial responses
        return getWebClient().get()
                .uri(url)
                .retrieve()
                .bodyToFlux(Recommendation.class)
                .log()
                .onErrorResume(error -> empty());
    }

    @Override
    public Recommendation createRecommendation(Recommendation body) {
        sendMessage(OUTPUT_RECOMMENDATIONS, new Event(CREATE, body.getProductId(), body));
        return body;
    }

    @Override
    public void deleteRecommendations(int productId) {
        sendMessage(OUTPUT_RECOMMENDATIONS, new Event(DELETE, productId, null));
    }

    @Override
    public Flux<Review> getReviews(int productId) {
        URI url = UriComponentsBuilder.fromUriString(reviewServiceUrl + "/review?productId={productId}").build(productId);
        LOG.debug("Calling the getReviews API on URL: {}", url);

        // Return an empty result if something goes wrong to make it possible for the composite service to return partial responses
        return getWebClient().get()
                .uri(url)
                .retrieve()
                .bodyToFlux(Review.class)
                .log()
                .onErrorResume(error -> empty());
    }

    @Override
    public Review createReview(Review body) {
        sendMessage(OUTPUT_REVIEWS, new Event(CREATE, body.getProductId(), body));
        return body;
    }

    @Override
    public void deleteReviews(int productId) {
        sendMessage(OUTPUT_REVIEWS, new Event(DELETE, productId, null));
    }

    private void sendMessage(String bindingName, Event event) {
        LOG.debug("Sending a {} message to {}", event.getEventType(), bindingName);
        Message<Event> message = MessageBuilder.withPayload(event).build();
        streamBridge.send(bindingName, message);
    }

    private WebClient getWebClient() {
        if(this.webClient == null) {
            this.webClient = this.webClientBuilder.build();
        }

        return webClient;
    }

    private Throwable handleException(Throwable ex) {
        if (!(ex instanceof WebClientResponseException)) {
            LOG.warn("Got a unexpected error: {}, will rethrow it", ex.toString());
            return ex;
        }

        WebClientResponseException wcre = (WebClientResponseException)ex;
        HttpStatus statusCode = HttpStatus.resolve(wcre.getStatusCode().value());

        if (statusCode != null) {
            switch (statusCode) {
                case NOT_FOUND:
                    return new NotFoundException(getErrorMessage(wcre));

                case UNPROCESSABLE_ENTITY:
                    return new InvalidInputException(getErrorMessage(wcre));

                default:
                    LOG.warn("Got an unexpected HTTP error: {}, will rethrow it", statusCode);
                    LOG.warn("Error body: {}", wcre.getResponseBodyAsString());
                    return ex;
            }
        }
        
        LOG.warn("Got an unexpected HTTP error with unknown status code, will rethrow it");
        return ex;
    }

    private String getErrorMessage(WebClientResponseException ex) {
        try {
            return mapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
        } catch (IOException ioex) {
            return ex.getMessage();
        }
    }
}
