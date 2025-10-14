package org.example.protushybrid.controller.dto;

import org.example.protushybrid.domain.core.LearningObject;

/**
 * Response containing recommended learning object with reasoning.
 */
public class RecommendationResponse {
    public Long loId;
    public String loType;
    public Long conceptId;
    public String conceptName;
    public Integer version;
    public String sourceUri;
    public Integer estTimeMin;

    // Reasoning metadata
    public String recommendationSource; // "mined", "cold_start", "intervention"
    public Double supportScore;
    public Double userScore;
    public Double totalScore;
    public Boolean isStuckIntervention;

    public static RecommendationResponse fromLearningObject(LearningObject lo) {
        var response = new RecommendationResponse();
        response.loId = lo.getId();
        response.loType = lo.getType();
        response.conceptId = lo.getConcept() != null ? lo.getConcept().getId() : null;
        response.conceptName = lo.getConcept() != null ? lo.getConcept().getName() : null;
        response.version = lo.getVersion();
        response.sourceUri = lo.getSourceUri();
        response.estTimeMin = lo.getEstTimeMin();
        return response;
    }

    public RecommendationResponse withReasoning(String source, Double support, Double user, Double total) {
        this.recommendationSource = source;
        this.supportScore = support;
        this.userScore = user;
        this.totalScore = total;
        return this;
    }

    public RecommendationResponse withIntervention(boolean isIntervention) {
        this.isStuckIntervention = isIntervention;
        return this;
    }
}
