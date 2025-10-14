package org.example.protushybrid.domain.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "concepts", schema = "app")
@Getter
@Setter
public class Concept {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    /**
     * Unique code identifier for the concept (e.g., "RECURSION", "VARIABLES")
     * Used for content file naming and lookups
     */
    @Column(length = 50, unique = true)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Course this concept belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    /**
     * Order index within the course
     */
    @Column(name = "order_index")
    private Integer orderIndex;

    /**
     * Prerequisite concepts that must be mastered first
     * Maps to concept_prerequisites table created in V4__table_changes.sql
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "concept_prerequisites",
        schema = "app",
        joinColumns = @JoinColumn(name = "concept_id"),
        inverseJoinColumns = @JoinColumn(name = "prerequisite_id")
    )
    private List<Concept> prerequisites = new ArrayList<>();
}
