package org.example.protushybrid.controller.dto;

/**
 * Request body for updating FSLSM style dimensions of a learner.
 * Accepts −11…+11 per dimension.
 */
public class StyleUpdateRequest {
    public Integer styleActiveReflective;
    public Integer styleSensingIntuitive;
    public Integer styleVisualVerbal;
    public Integer styleSequentialGlobal;
}
