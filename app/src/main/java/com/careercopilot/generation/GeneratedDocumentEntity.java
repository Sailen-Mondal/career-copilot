package com.careercopilot.generation;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "generated_document")
public class GeneratedDocumentEntity {

    @Id
    private UUID id;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DocumentType type;

    @Column(nullable = false)
    private String content;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_fact_ids", nullable = false, columnDefinition = "jsonb")
    private List<UUID> sourceFactIds = new ArrayList<>();

    public GeneratedDocumentEntity() {
    }

    public GeneratedDocumentEntity(GeneratedDocument domain) {
        this.id = domain.id();
        this.applicationId = domain.applicationId();
        this.type = domain.type();
        this.content = domain.content();
        this.generatedAt = domain.generatedAt();
        this.sourceFactIds = domain.sourceFactIds();
    }

    public GeneratedDocument toDomain() {
        return new GeneratedDocument(
                this.id,
                this.applicationId,
                this.type,
                this.content,
                this.generatedAt,
                this.sourceFactIds
        );
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public DocumentType getType() { return type; }
    public void setType(DocumentType type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public List<UUID> getSourceFactIds() { return sourceFactIds; }
    public void setSourceFactIds(List<UUID> sourceFactIds) { this.sourceFactIds = sourceFactIds; }
}
