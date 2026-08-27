package com.atcsafety.restapi.infrastructure;

import java.time.Instant;

/**
 * Embedded MongoDB sub-document representing a reviewer comment.
 *
 * <p>Stored inside {@link InfringementEventDocument#getComments()}.
 * Maps to the domain {@link com.atcsafety.restapi.domain.Comment} record.
 */
class CommentDocument {

    private String id;
    private String text;
    private String author;
    private Instant createdAt;

    CommentDocument() {
    }

    CommentDocument(String id, String text, String author, Instant createdAt) {
        this.id = id;
        this.text = text;
        this.author = author;
        this.createdAt = createdAt;
    }

    String getId() { return id; }
    String getText() { return text; }
    String getAuthor() { return author; }
    Instant getCreatedAt() { return createdAt; }
}
