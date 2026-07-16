package com.careercopilot.automation;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "answer_cache")
public class AnswerCacheEntity {

    @Id
    private UUID id;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "question_embedding", nullable = false, columnDefinition = "vector(1536)")
    private float[] questionEmbedding;

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "scope")
    private String scope;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AnswerCacheEntity() {
    }

    public AnswerCacheEntity(UUID id, String questionText, float[] questionEmbedding, String answerText, String scope, Instant createdAt) {
        this.id = id;
        this.questionText = questionText;
        this.questionEmbedding = questionEmbedding;
        this.answerText = answerText;
        this.scope = scope;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public float[] getQuestionEmbedding() { return questionEmbedding; }
    public void setQuestionEmbedding(float[] questionEmbedding) { this.questionEmbedding = questionEmbedding; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
