# Project Overview - HybridTuS Inner Workings

**Quick Technical Reference**
**Version:** 1.0
**Last Updated:** January 2025

---

## Table of Contents

1. [Recommendation Engine](#recommendation-engine)
2. [Sequence Mining](#sequence-mining)
3. [Mastery Tracking](#mastery-tracking)
4. [Data Models](#data-models)
5. [Key Algorithms](#key-algorithms)
6. [API Endpoints](#api-endpoints)

---

## Recommendation Engine

### Architecture

**File:** `RecommendationService.java`

**Inputs:**
- `learnerId` - User identifier
- `conceptId` - Target concept (optional, defaults to lowest mastery)
- Active session with `completionSequence`

**Process:**

```
1. EXTRACT CONTEXT
   cluster_key = ClusterKeyUtil.forLearner(learner)
   // e.g., "ACTIVE_SENSING_VISUAL_SEQUENTIAL"

   suffix = session.completionSequence.last(3 items)
   // e.g., ["T", "E", "A"]

   available_types = concept.learningObjects.types - session.visitedTypes
   // e.g., {Test, X, R}

2. QUERY MINED PATTERNS (with backoff)

   Try suffix lengths: 3 → 2 → 1 → 0 (start state)

   For each suffix length:
     a. Query cluster-specific:
        SELECT * FROM mined_supports
        WHERE cluster_key = 'ACTIVE_SENSING_...'
          AND concept_id = 42
          AND suffix = 'E>A'  // 2-gram example
        ORDER BY n_support DESC

     b. Fallback to GLOBAL:
        WHERE cluster_key = 'GLOBAL'
          AND suffix = 'E>A'

   Return first available pattern (next_type in available_types)

3. COLD-START FALLBACK (if no patterns)

   DefaultSequenceProvider.getNext(cluster_key, visited_types)

   Uses predefined FSLSM sequences:
   - ACTIVE_SENSING_VISUAL_SEQUENTIAL: T→F→E→A→X→Test
   - REFLECTIVE_INTUITIVE_VERBAL_SEQUENTIAL: T→R→A→Test
   - ... (16 cluster-specific sequences)

4. SELECT SPECIFIC LEARNING OBJECT

   candidates = LOs of recommended_type in concept
   recommended_lo = candidates.newest_version (or random)

   Return recommended_lo.id
```

**Output:**
- `recommendedModuleId` - Specific LO to highlight
- `reasoning` - "mined" or "cold-start"
- `isStuckIntervention` - Warning flag (evaluation mode)

---

## Sequence Mining

### Mining Pipeline

**File:** `SequenceMiningService.java`

**Trigger:** Admin clicks "Run Sequence Mining" → POST `/api/admin/mining/run`

**Process:**

```
1. LOAD COMPLETED SESSIONS

   sessions = SELECT * FROM learning_sessions
              WHERE completed = true
              JOIN learners ON learner_id

2. GROUP BY CLUSTER

   clusters = GROUP sessions BY FSLSM_cluster_key
   // e.g., {
   //   "ACTIVE_SENSING_VISUAL_SEQUENTIAL": [session1, session2, ...],
   //   "REFLECTIVE_INTUITIVE_VERBAL_GLOBAL": [session10, session11, ...],
   // }

3. FOR EACH CLUSTER, GROUP BY CONCEPT

   partitions = GROUP cluster_sessions BY concept_id
   // e.g., {
   //   42 (Recursion): [session1, session2, ...],
   //   43 (Loops): [session5, session6, ...]
   // }

4. FOR EACH PARTITION (cluster, concept):

   a. THRESHOLD CHECK
      IF total_events < 30 OR unique_learners < 5:
        SKIP partition

   b. EXTRACT SEQUENCES
      For each session:
        raw_sequence = [T₁, T₁, T₂, E₁, A₁, A₁, A₂]

        Collapse contiguous same-LO-ID duplicates:
        collapsed = [T, T, E, A, A]
        // T₁→T₁ collapsed, but T₂ kept
        // A₁→A₁ collapsed, but A₂ kept

   c. GENERATE N-GRAMS

      From sequence [T, T, E, A, A]:

      Start state:
        ∅ → T

      1-grams (i=1,2,3,4):
        T → T  (position 0→1)
        T → E  (position 1→2)
        E → A  (position 2→3)
        A → A  (position 3→4)

      2-grams (i=2,3,4):
        T>T → E  (positions 0,1→2)
        T>E → A  (positions 1,2→3)
        E>A → A  (positions 2,3→4)

      3-grams (i=3,4):
        T>T>E → A  (positions 0,1,2→3)
        T>E>A → A  (positions 1,2,3→4)

   d. AGGREGATE ACROSS LEARNERS

      Track per pattern:
        - learner_support: Set<learner_id> who exhibited pattern
        - pattern_count: Total occurrences

      Example:
        Pattern "T>E → A":
          learner_support = {42, 15, 89, 103, ...}  // 18 learners
          pattern_count = 45 occurrences

   e. COMPUTE SUPPORT & FILTER

      For each pattern:
        distinct_learners = SIZE(learner_support)

        IF distinct_learners < 5:
          DISCARD  // Below MIN_LEARNERS

        support = distinct_learners / total_learners_in_partition

        IF support < 0.25:
          DISCARD  // Below MIN_SUPPORT

        Keep pattern for storage

   f. SAVE TO DATABASE

      DELETE FROM mined_supports
      WHERE cluster_key = current_cluster
        AND concept_id = current_concept

      INSERT patterns (batch insert for performance)

5. MINE GLOBAL FALLBACK

   Repeat steps 3-4 with cluster_key = "GLOBAL"
   Uses ALL learners regardless of cluster
```

**Output:**
- Patterns stored in `mined_supports` table
- Used by recommendation engine for real-time queries

---

## Mastery Tracking

### EWMA Algorithm

**File:** `MasteryService.java`

**Formula:**
```
mastery_new = α × outcome + (1 - α) × mastery_old
```

**Constants:**
```java
ALPHA = 0.4                // Weight for new outcome
MASTERY_THRESHOLD = 0.7    // Mastered threshold
WORKING_LOW = 0.65         // Working band lower bound
MIN_EVENTS = 3             // Minimum events before showing band
```

**Score Mapping:**
```java
scoreRaw (1-5) → outcome (0.0-1.0):
  4-5 → 1.0  (success)
  3   → 0.5  (partial)
  1-2 → 0.0  (fail)
```

**Process:**

```
1. User submits exercise answer
   └─> POST /api/exercises/grade
       └─> LlmGradingService.grade()
           └─> Returns scoreRaw (0-5)

2. Record interaction
   └─> POST /api/interactions
       └─> InteractionService.recordGraded()
           ├─> Apply anti-gaming filters
           ├─> Save interaction
           └─> Call MasteryService.updateMastery()

3. Update mastery
   └─> MasteryService.updateMastery(learnerId, conceptId, scoreRaw)
       ├─> Load current mastery (or 0.0 if first event)
       ├─> Map scoreRaw → outcome
       ├─> new_mastery = 0.4 × outcome + 0.6 × old_mastery
       ├─> Increment event_count
       └─> Save to mastery table

4. Compute mastery band
   └─> MasteryService.getMasteryLabel(mastery, event_count)
       IF event_count < 3:
         RETURN null  // Suppress label
       ELSE IF mastery >= 0.70:
         RETURN "Mastered"
       ELSE IF mastery >= 0.65:
         RETURN "Working"
       ELSE:
         RETURN "Unsafe"
```

**Example Progression:**

```
Event 1: Score 4/5 → outcome=1.0
  mastery = 0.4 × 1.0 + 0.6 × 0.0 = 0.40
  Band: null (event_count=1 < 3)

Event 2: Score 5/5 → outcome=1.0
  mastery = 0.4 × 1.0 + 0.6 × 0.40 = 0.64
  Band: null (event_count=2 < 3)

Event 3: Score 4/5 → outcome=1.0
  mastery = 0.4 × 1.0 + 0.6 × 0.64 = 0.78
  Band: "Mastered" (event_count=3 >= 3, mastery >= 0.70)

Event 4: Score 2/5 → outcome=0.0
  mastery = 0.4 × 0.0 + 0.6 × 0.78 = 0.47
  Band: "Unsafe" (mastery < 0.65)
```

---

## Data Models

### Entity Relationships

```
Learner (1) ────< (N) LearningSession ────> (1) Concept
   │                      │                        │
   │                      │                        │
   ├─ (1:1) FslsmProfile  │                        └─< (N) LearningObject
   │                      │
   └─< (N) Interaction    └───< (N) session_completion_sequence
   │                          └───< (N) session_done_modules
   └─< (N) Mastery

MinedSupport ──> Concept (via concept_id)
             ──> cluster_key (FSLSM cluster string)
```

### Key Entities

**Learner**
```java
@Entity @Table(name = "learners", schema = "app")
class Learner {
    Long id;
    String externalRef;
    String displayName;
    Double styleActiveReflective;    // -11 to +11
    Double styleSensingIntuitive;    // -11 to +11
    Double styleVisualVerbal;        // -11 to +11
    Double styleSequentialGlobal;    // -11 to +11
}
```

**LearningSession**
```java
@Entity @Table(name = "learning_sessions", schema = "app")
class LearningSession {
    String sessionId;  // UUID
    Learner learner;
    Concept concept;
    List<Long> visitedLoIds;       // Navigation (no duplicates)
    Set<Long> doneLoIds;           // First-time completions
    List<Long> completionSequence; // Done + revisits
    Boolean completed;
    Instant lastAccessedAt;
}
```

**MinedSupport**
```java
@Entity @Table(name = "mined_supports", schema = "app")
class MinedSupport {
    Long id;
    String clusterKey;      // e.g., "ACTIVE_SENSING_VISUAL_SEQUENTIAL"
    Long conceptId;
    String suffix;          // e.g., "T>E" or "" (start state)
    String nextType;        // e.g., "A"
    Double support;         // 0.0-1.0 (e.g., 0.78 = 78%)
    Integer nSupport;       // Occurrence count
    Integer nLearners;      // Distinct learners
    Instant lastMinedAt;
}
```

**Mastery**
```java
@Entity @Table(name = "mastery", schema = "app")
class Mastery {
    Long learnerId;
    Long conceptId;
    Double mastery;        // EWMA score (0.0-1.0)
    Integer eventCount;
    Instant lastUpdated;
}
```

---

## Key Algorithms

### 1. FSLSM Cluster Assignment

**File:** `ClusterKeyUtil.java`

```java
public static String forLearner(Learner learner) {
    String ar = learner.getStyleActiveReflective() >= 0
        ? "ACTIVE" : "REFLECTIVE";
    String si = learner.getStyleSensingIntuitive() >= 0
        ? "SENSING" : "INTUITIVE";
    String vv = learner.getStyleVisualVerbal() >= 0
        ? "VISUAL" : "VERBAL";
    String sg = learner.getStyleSequentialGlobal() >= 0
        ? "SEQUENTIAL" : "GLOBAL";

    return ar + "_" + si + "_" + vv + "_" + sg;
}
```

**Example:**
- Style values: `{ar: 5.0, si: -3.0, vv: 7.0, sg: -2.0}`
- Cluster: `"ACTIVE_INTUITIVE_VISUAL_GLOBAL"`

### 2. Suffix Extraction

**File:** `RecommendationService.java`

```java
private List<String> extractSuffix(Long learnerId, Long conceptId) {
    // Find active session
    LearningSession session = findActiveSession(learnerId, conceptId);

    // Extract LO types from completion sequence
    List<String> types = session.getCompletionSequence().stream()
        .map(loId -> loRepo.findById(loId))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(lo -> normalizeToCanonical(lo.getType()))
        .collect(Collectors.toList());

    // Return last MAX_SUFFIX_LENGTH (3) types
    int start = Math.max(0, types.size() - 3);
    return types.subList(start, types.size());
}
```

**Example:**
- Completion sequence: `[101, 102, 105, 108, 109]`
- LO types: `[T, T, E, A, A]`
- Suffix (last 3): `["E", "A", "A"]`

### 3. Anti-Gaming Filters

**File:** `InteractionService.java`

```java
public boolean recordGraded(Long learnerId, Long loId,
                           Integer scoreRaw, Integer duration) {
    // 1. Duration filter
    if (duration < MIN_DURATION_GRADED_SEC) {
        log.warn("Rejected: duration {} < {}", duration, MIN_DURATION_GRADED_SEC);
        return false;
    }

    // 2. Bot detection (≥60 completions/hour)
    int recentCount = countRecentCompletions(learnerId, 60 minutes);
    if (recentCount >= BOT_THRESHOLD_PER_HOUR) {
        log.warn("Rejected: bot threshold exceeded");
        return false;
    }

    // 3. Rate limiting (>3 repeats per LO per hour)
    int repeatCount = countRecentRepeats(learnerId, loId, 60 minutes);
    if (repeatCount > MAX_REPEATS_PER_HOUR) {
        log.warn("Rejected: repeat rate limit");
        return false;
    }

    // All checks passed
    recordInteraction(learnerId, loId, scoreRaw, duration);
    updateMastery(learnerId, conceptId, scoreRaw);
    return true;
}
```

**Thresholds:**
```java
MIN_DURATION_VIEW_SEC = 5      // View interactions
MIN_DURATION_GRADED_SEC = 30   // Graded interactions
BOT_THRESHOLD_PER_HOUR = 60    // Max completions/hour
MAX_REPEATS_PER_HOUR = 3       // Max repeats per LO
```

---

## API Endpoints

### Core Endpoints

**Sessions**
- `GET /api/sessions?learnerId={id}&conceptId={id}` - Get or create session
- `POST /api/sessions/visit?sessionId={id}&loId={id}` - Record visit
- `POST /api/sessions/mark-done?sessionId={id}&loId={id}` - Mark completed

**Recommendations**
- `GET /api/recommendations/next?learnerId={id}&conceptId={id}` - Get recommendation
- `GET /api/recommendations/trace?learnerId={id}&conceptId={id}&limit={n}` - Debug trace

**Interactions**
- `POST /api/interactions` - Record interaction (view or graded)

**Exercises**
- `POST /api/exercises/generate` - Generate exercise via LLM
- `POST /api/exercises/grade` - Grade exercise via LLM

**Chat**
- `POST /api/chat` - AI conversational support

**Admin**
- `POST /api/admin/mining/run` - Trigger sequence mining
- `POST /api/admin/data/generate` - Generate synthetic data
- `GET /api/admin/status` - System status

### Response Examples

**Session Response:**
```json
{
  "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "conceptId": 42,
  "conceptTitle": "Recursion",
  "learningObjects": [
    {
      "id": 101,
      "type": "T",
      "title": "Introduction to Recursion",
      "sourceUri": "/content/recursion/intro.md"
    }
  ],
  "visitedLoIds": [101, 102],
  "doneLoIds": [101],
  "recommendedModuleId": 105,
  "completionPercentage": 0.25
}
```

**Recommendation Response:**
```json
{
  "learningObjectId": 105,
  "type": "E",
  "title": "Factorial Example",
  "reasoning": "mined",
  "confidence": 0.78,
  "isStuckIntervention": false
}
```

**Mastery Response:**
```json
{
  "learnerId": 1,
  "conceptId": 42,
  "mastery": 0.78,
  "eventCount": 5,
  "label": "Mastered",
  "lastUpdated": "2025-01-15T10:30:00Z"
}
```

---

**Document Version:** 1.0
**Last Updated:** January 2025
**For More Details:** See `docs/` folder for comprehensive documentation
