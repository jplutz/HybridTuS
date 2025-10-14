package org.example.protushybrid.domain.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "generated_los", schema = "app")
public class GeneratedLo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_lo_id")
    private Long baseLoId;

    @Column(name = "concept_id", nullable = false)
    private Long conceptId;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "prompt_hash", length = 64)
    private String promptHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> contentJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "human_reviewed", nullable = false)
    private Boolean humanReviewed = Boolean.FALSE;
}
