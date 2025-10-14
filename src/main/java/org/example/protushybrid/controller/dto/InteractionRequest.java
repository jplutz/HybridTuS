package org.example.protushybrid.controller.dto;

public class InteractionRequest {
    public Long learnerId;
    public Long loId;
    public Double score;        // 0..1 (deprecated, use scoreRaw)
    public Short scoreRaw;      // 1-5 scale (5-4=success, 3=partial, 2-1=fail)
    public Integer hintCount;   // Number of hints used
    public Integer durationSec; // Time spent in seconds
}
