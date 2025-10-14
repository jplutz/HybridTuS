# HybridTuS - Adaptive Learning System

**Version:** 1.0
**Project:** Bachelor Thesis Prototype
**Last Updated:** January 2025

---

## Overview

HybridTuS (Hybrid Tutoring System) is an adaptive learning platform that combines:

- **FSLSM Learning Styles** - Personalized content recommendations based on Felder-Silverman model
- **Sequence Mining** - Pattern discovery from learner behavior using AprioriAll/PrefixSpan algorithms
- **AI Integration** - Claude 3.5 Sonnet for exercise generation, grading, and conversational support
- **Hybrid Recommendations** - Data-driven suggestions with pedagogically-grounded fallbacks

### Core Philosophy

**Recommendation, Not Prescription**: The system suggests optimal learning paths based on group patterns and learning styles, but learners maintain full autonomy over their learning journey.

---

## Quick Start

### Prerequisites

| Software | Version | Purpose |
|----------|---------|---------|
| Java JDK | 21+ | Backend runtime |
| Maven | 3.8+ | Build tool |
| Node.js | 18+ | Frontend runtime |
| Docker | Latest | Database container |
| Git | Latest | Version control |

### Installation

**1. Start Database**
```bash
docker run --name protus-postgres \
  -e POSTGRES_DB=protus_hybrid \
  -e POSTGRES_USER=protus \
  -e POSTGRES_PASSWORD=changeMe \
  -p 5432:5432 \
  -d postgres:16
```

**2. Configure Backend**

Create environment variables (IntelliJ Run Configuration):
```properties
DB_URL=jdbc:postgresql://localhost:5432/protus_hybrid
DB_USER=protus
DB_PASS=changeMe
ANTHROPIC_API_KEY=sk-ant-api03-YOUR_KEY_HERE
```

**3. Configure Frontend**

```bash
cd frontend
echo "VITE_API_BASE_URL=http://localhost:8080" > .env
npm install
```

**4. Run Application**

Terminal 1 - Backend:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Terminal 2 - Frontend:
```bash
cd frontend
npm run dev
```

**5. Access Application**
- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- Health Check: http://localhost:8080/actuator/health

---

## System Architecture

### Technology Stack

**Backend**
- Spring Boot 3.3.4 (Java 21)
- PostgreSQL 16
- Hibernate/JPA
- Flyway migrations
- Bucket4j rate limiting

**Frontend**
- React 18.3 + TypeScript
- Vite 5.4
- Tailwind CSS
- Zustand state management

**External Services**
- Anthropic Claude 3.5 Sonnet API
- Docker PostgreSQL


---

## Key Features

### 1. Adaptive Recommendations

**How it Works:**
1. Extract learner's FSLSM cluster (e.g., ACTIVE_SENSING_VISUAL_SEQUENTIAL)
2. Analyze recent learning sequence (e.g., completed Theory → Example)
3. Query mined patterns: What do similar learners typically do next?
4. Recommend next Learning Object with highest support (e.g., Activity)
5. Fallback to FSLSM-based sequences when no mined data exists

**Example:**
```
Learner completes: [Theory, Example]
System checks: Pattern "T>E" for cluster "ACTIVE_SENSING_VISUAL_SEQUENTIAL"
Mined data shows: 78% of similar learners completed Activity next
Recommendation: Highlight Activity module as "Recommended"
```

### 2. Sequence Mining

**Algorithm:** AprioriAll with n-gram suffix matching (1-3 length)

**Process:**
1. Collect completed learning sessions
2. Group by FSLSM cluster and concept
3. Extract LO type sequences (e.g., T→E→A→Test)
4. Generate n-gram patterns with support values
5. Store patterns meeting thresholds (min 5 learners, 25% support)
6. Use patterns for real-time recommendations

**Mining Thresholds:**
- MIN_LEARNERS: 5 distinct learners
- MIN_SUPPORT: 0.25 (25% of learners)
- MIN_EVENTS: 30 completion events per partition
- MAX_SUFFIX: 3 (trigram patterns)

### 3. Mastery Tracking

**Algorithm:** EWMA (Exponentially Weighted Moving Average)

**Formula:** `mastery_new = 0.4 × outcome + 0.6 × mastery_old`

**Mastery Bands:**
- **< 0.65** - Unsafe (needs practice)
- **0.65-0.69** - Working (developing)
- **≥ 0.70** - Mastered (proficient)

**Score Mapping:**
- Scores 4-5 → 1.0 (success)
- Score 3 → 0.5 (partial)
- Scores 1-2 → 0.0 (fail)

### 4. AI-Powered Features

**Exercise Generation:**
- Dynamic exercise creation based on concept and difficulty
- Supports multiple types: multiple-choice, free-text, code-write

**Grading:**
- Intelligent scoring with detailed feedback
- Anti-gaming filters (minimum time thresholds)
- Graceful fallback on LLM failures

**Conversational Support:**
- Context-aware chat assistance
- Concept-specific guidance
- Real-time learner support

### 5. Security & Rate Limiting

**Security Layers:**
1. **Localhost Binding** - No external network access
2. **CORS Protection** - Only allows frontend origin
3. **API Token Authentication** - Prevents unauthorized access
4. **Rate Limiting** - Prevents abuse (10 LLM requests/min, 60 mutations/min)

---

## Database Schema

### Core Tables

**learners** - User profiles with FSLSM dimensions
- `id`, `display_name`, `external_ref`
- `style_active_reflective`, `style_sensing_intuitive`
- `style_visual_verbal`, `style_sequential_global`

**learning_sessions** - Session tracking
- `session_id`, `learner_id`, `concept_id`
- `visited_lo_ids` (navigation), `done_lo_ids` (completed)
- `completion_sequence` (includes revisits)

**learning_objects** - Content units
- `id`, `concept_id`, `type` (T, E, A, F, Test, X, R, S, O)
- `title`, `source_uri`, `version`

**mined_supports** - Discovered patterns
- `cluster_key`, `concept_id`, `suffix`, `next_type`
- `support` (0.0-1.0), `n_support`, `n_learners`

**mastery** - Learner proficiency
- `learner_id`, `concept_id`, `mastery` (EWMA score)
- `event_count`, `last_updated`

---

## Testing

### Test Suite (103 Tests)

**Unit Tests (79 tests):**
- MasteryServiceTest (16) - EWMA calculations
- SequenceMiningServiceTest (11) - Pattern discovery
- RecommendationServiceTest (6) - Suffix matching
- InteractionServiceTest (18) - Anti-gaming filters
- LlmGradingServiceTest (28) - AI grading
- ApiTokenFilterTest (13) - Security

**Integration Tests (24 tests):**
- MigrationSmokeTest (10) - Database schema
- RecommendationControllerIT (15) - API endpoints
- LlmGradingControllerIT (9) - AI integration

**Run Tests:**
```bash
# All tests
mvn test

# Unit tests only
mvn test -Dtest=**/*Test

# Integration tests only
mvn test -Dtest=**/*IT

# With coverage
mvn test jacoco:report
```

**Test Infrastructure:**
- Testcontainers for real PostgreSQL 16 instances
- Mockito for dependency mocking
- AssertJ for fluent assertions

---

## Development Workflow

### 1. Explore the Application

1. Browse courses: http://localhost:5173/courses
2. Select a learner from header dropdown
3. Start a learning session
4. Complete learning objects and receive recommendations
5. Try practice mode with AI-generated exercises
6. Use chat assistant for help

### 2. Admin Features

Access admin panel: http://localhost:5173/admin

**Pipeline Tab:**
- Generate synthetic learners and sessions
- Run sequence mining
- View mining statistics

**Flagged Exercises Tab:**
- Review problematic exercises
- View failure patterns
- Manage exercise quality

### 3. Database Management

**Using DBeaver (Recommended):**

1. Install DBeaver Community
2. Create connection:
   - Host: localhost
   - Port: 5432
   - Database: protus_hybrid
   - User: protus
   - Password: changeMe
3. Explore tables in `app` schema
4. Run SQL queries
5. View ER diagrams


---

## Troubleshooting

### Database Connection Refused
```bash
# Check container status
docker ps -a | grep protus-postgres

# Start container
docker start protus-postgres

# View logs
docker logs protus-postgres -f
```

### Port Already in Use
```bash
# Windows
netstat -ano | findstr :8080

# Mac/Linux
lsof -i :8080

# Change port in application-dev.yml if needed
```

### Frontend Can't Reach Backend
1. Verify backend running: `curl http://localhost:8080/actuator/health`
2. Check frontend `.env` file exists
3. Restart frontend: Ctrl+C, then `npm run dev`

### API Token Error (403 Forbidden)
1. Verify token matches in both:
   - Backend: `src/main/resources/application-dev.yml`
   - Frontend: `frontend/src/config/api.ts`
2. Default token: `local-dev-token-12345`
3. Restart both applications

### Rate Limit Exceeded (429)
- **Wait** for retry time indicated in response
- **Increase limits** in `application-dev.yml`:
  ```yaml
  rate-limit:
    llm-requests: 20  # Increase from 10
  ```
- Restart backend

---

## Documentation

### Available Guides

- **[INFRASTRUCTURE.md](docs/INFRASTRUCTURE.md)** - System architecture, deployment, testing infrastructure
- **[RECOMMENDATION_SYSTEM.md](docs/RECOMMENDATION_SYSTEM.md)** - Recommendation engine, FSLSM cold-start, stuck detection
- **[SEQUENCE_MINING.md](docs/SEQUENCE_MINING.md)** - Mining algorithms, pattern discovery, n-gram generation
- **[SECURITY.md](docs/SECURITY.md)** - API security, CORS, rate limiting, token authentication
- **[TESTING.md](docs/TESTING.md)** - Test suite, writing tests, CI/CD integration
- **[PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)** - Quick technical overview of inner workings

---

## Acknowledgments

- **FSLSM**: Felder-Silverman Learning Style Model
- **Spring Boot**: Application framework
- **React**: Frontend library
- **PostgreSQL**: Database
- **Anthropic Claude**: AI integration
- **Testcontainers**: Testing infrastructure

---

**Version**: 1.0
**Last Updated**: October 2025

