package org.example.protushybrid.domain.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a learner profile based on the Felder–Silverman Learning Style Model (FSLSM).
 *
 * Each dimension arises from 11 forced-choice items in the ILS questionnaire.
 * Scores range from −11 to +11:
 *   −11 … −9  = strong preference for first pole
 *   −7 … −3   = moderate preference
 *   −1 … +1   = balanced
 *   +3 … +7   = moderate preference for opposite pole
 *   +9 … +11  = strong preference
 *
 * Poles:
 *   Active–Reflective          (− = Reflective, + = Active)
 *   Sensing–Intuitive          (− = Intuitive, + = Sensing)
 *   Visual–Verbal              (− = Verbal, + = Visual)
 *   Sequential–Global          (− = Global, + = Sequential)
 */
@Entity
@Table(schema = "app", name = "learners")
@Getter
@Setter
public class Learner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** External identifier (e.g., username or token) */
    @Column(name = "external_ref", unique = true)
    private String externalRef;

    /** Display name for UI presentation */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "style_active_reflective", nullable = false)
    private Double styleActiveReflective = 0.0;

    @Column(name = "style_sensing_intuitive", nullable = false)
    private Double styleSensingIntuitive = 0.0;

    @Column(name = "style_visual_verbal", nullable = false)
    private Double styleVisualVerbal = 0.0;

    @Column(name = "style_sequential_global", nullable = false)
    private Double styleSequentialGlobal = 0.0;

}
