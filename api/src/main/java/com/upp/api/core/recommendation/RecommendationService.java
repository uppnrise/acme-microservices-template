package com.upp.api.core.recommendation;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

public interface RecommendationService {

    /**
     * Sample usage:
     * <p>
     * curl $HOST:$PORT/recommendation?productId=1
     *
     * @param productId productId
     * @return list of recommendation
     */
    @GetMapping(
            value = "/recommendation",
            produces = "application/json")
    Flux<Recommendation> getRecommendations(@RequestParam(value = "productId") int productId);

    /**
     * Sample usage:
     *
     * curl -X POST $HOST:$PORT/recommendation \
     * -H "Content-Type: application/json" --data \
     * '{"productId":123,"recommendationId":456,"author":"me","rate":5,"content":"yada, yada, yada"}'
     *
     * @param body request
     * @return recommendation
     */
    @PostMapping(
            value = "/recommendation",
            consumes = "application/json",
            produces = "application/json")
    Recommendation createRecommendation(@RequestBody Recommendation body);

    /**
     * Sample usage:
     * <p>
     * curl -X DELETE $HOST:$PORT/recommendation?productId=1
     *
     * @param productId productId
     */
    @DeleteMapping(value = "/recommendation")
    void deleteRecommendations(@RequestParam(value = "productId") int productId);
}
