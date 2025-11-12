package com.upp.microservices.core.review.persistence;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

import static java.lang.String.format;

@Data
@NoArgsConstructor
@Entity
@Table(name = "reviews", indexes = { @Index(name = "reviews_unique_idx", unique = true, columnList = "productId,reviewId") })
public class ReviewEntity {

    @Id
    @GeneratedValue
    private int id;

    @Version
    private int version;

    private int productId;
    private int reviewId;
    private String author;
    private String subject;
    private String content;

    public ReviewEntity(int productId, int reviewId, String author, String subject, String content) {
        this.productId = productId;
        this.reviewId = reviewId;
        this.author = author;
        this.subject = subject;
        this.content = content;
    }

    @Override
    public String toString() {
        return format("ReviewEntity: %s/%d", productId, reviewId);
    }
}
