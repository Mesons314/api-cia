# API-CIA (API Change Impact Analyzer)

> **Detect API contract changes, analyze security and governance violations, identify downstream consumers, and estimate change impact before deployment.**

---

## 1. System Architecture & Core Pipeline

API-CIA is a developer and security analysis platform that automates API evolution risk assessment through a 5-stage pipeline:

```
[ OpenAPI Specs / Source Code AST ]
                 │
                 ▼
┌────────────────────────────────────────────────────────┐
│ 1. Static Governance Module (SGM)                      │
│    • SGM-001 to SGM-014 rule evaluations               │
│    • Breaking parameter, response & route mutations    │
│    • Structural Damage Score: D_struct                 │
└────────────────────────┬───────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│ 2. Security Analysis Module (SAM)                      │
│    • Wildcard CORS detection (SAM-001)                 │
│    • Sensitive data leak scanning (SAM-002 / SAM-003)  │
│    • Unauthenticated public endpoints (SAM-004)        │
│    • Circular references & N+1 queries (SAM-005/007)   │
└────────────────────────┬───────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│ 3. Downstream Dependency Scanner                       │
│    • JavaParser AST inspection of consumer services    │
│    • Spring Cloud FeignClient mappings                 │
│    • RestTemplate & WebClient URI call sites           │
└────────────────────────┬───────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│ 4. Blast Radius & Composite Risk Engine                │
│    • Consumer service breakage propagation             │
│    • Blast Radius Factor: D_blast                      │
│    • S_total = min(1, 0.70 × D_struct + 0.30 × D_blast)│
│    • Risk Level: LOW / MEDIUM / HIGH / CRITICAL        │
└────────────────────────┬───────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│ 5. Developer Tooling & Integrations                    │
│    • Multi-language SDK generation (TypeScript, Java)  │
│    • Postman Collection v2.1 export                    │
│    • Git Pre-Push Hook integration                     │
└────────────────────────────────────────────────────────┘
```

---

## 2. Prerequisites

- **Java**: JDK 17 or 21
- **Node.js**: v18+ (v24 LTS recommended)
- **Database**: PostgreSQL (port 5432, database: `api_analyzer`) or MySQL
- **Build Tools**: Maven (`mvnw.cmd` included), `npm`

---

## 3. Starting the Backend

1. **Configure Database & Properties**:
   Ensure PostgreSQL is running and update `backend/api-cia/src/main/resources/application.properties` if needed:
   ```properties
   server.port=8080
   spring.datasource.url=jdbc:postgresql://localhost:5432/api_analyzer
   spring.datasource.username=postgres
   spring.datasource.password=your_password
   ```

2. **Run Spring Boot Application**:
   ```bash
   cd backend/api-cia
   ./mvnw.cmd spring-boot:run
   ```
   The backend starts at `http://localhost:8080`.

---

## 4. Starting the Frontend

1. **Navigate to the frontend directory**:
   ```bash
   cd frontend
   ```

2. **Configure Environment** (defaults to `http://localhost:8080`):
   ```bash
   # .env
   VITE_API_BASE_URL=http://localhost:8080
   ```

3. **Install Dependencies & Launch Dev Server**:
   ```bash
   npm install
   npm run dev
   ```
   The UI will open at `http://localhost:3000`.

---

## 5. 3–5 Minute Evaluator Demo Flow

1. **Open Dashboard (`/`)**:
   - Observe live backend connection indicator in the sidebar (`● Connected`).
   - Review aggregate summary cards: API Specs, Reports, Affected Consumers, and High/Critical Risk count.

2. **Execute Analysis (`/analyze`)**:
   - Click **"Launch Demo Analysis"** (1-click Evaluator Preset) or select/upload `openapi_v1.json` vs `openapi_v2.json`.
   - Watch the 5-step pipeline execution tracker.

3. **Review "What Changed?" (SGM Governance)**:
   - Inspect detected breaking changes (e.g. parameter `id` type changed `integer` $\rightarrow$ `string`, new required field `age` added, removed security requirements).
   - Click on any finding to open the **Slide-Over Detail Inspector** showing the exact contract diff and code location.

4. **Review Blast Radius & Consumers (`/blast-radius`)**:
   - Inspect the interactive downstream microservice tree diagram:
     `Provider API` $\rightarrow$ `GET /v1/users` $\rightarrow$ `Order Service (Feign)` / `Payment Service (RestTemplate)`.
   - View exact file names and line numbers of breaking client call sites.

5. **Examine Composite Risk Scoring**:
   - Review the mathematical formula breakdown:
     $$S_{total} = \min(1, 0.70 \times D_{struct} + 0.30 \times D_{blast})$$
   - View the calculated risk level badge (`HIGH` / `CRITICAL`).

6. **Inspect Security & Compliance Findings (`/security`)**:
   - Review SAM findings: Wildcard CORS, sensitive data exposures (`passwordHash` in response DTO), and unauthenticated endpoints.

7. **Generate Client SDK & Postman Collection (`/sdk-generator` & `/postman-generator`)**:
   - Select the target contract and download a compiled TypeScript Axios SDK ZIP or Postman v2.1 test collection in 1 click.