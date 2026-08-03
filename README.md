# API-CIA Repository Analysis

## Repository
`c:\Users\Disha Dungrani\api-cia`

---

# 1. Directory / Project Structure

```
api-cia/
├── backend/
│   └── api-cia/                          ← Actual Maven/Spring Boot module
│       ├── .mvn/
│       ├── mvnw
│       ├── mvnw.cmd
│       ├── pom.xml
│       ├── samples/
│       │   ├── openapi_v1.json
│       │   └── openapi_v2.json
│       ├── automatic_integration_design.md
│       ├── code_changes_guide.md
│       ├── dependency_analyzer_design.md
│       ├── direct_code_parser_guide.md
│       ├── universal_integration_guide.md
│       └── src/
│           └── main/
│               ├── java/com/apicia/
│               │   ├── ApiciaApplication.java
│               │   ├── config/
│               │   ├── controller/
│               │   ├── exception/
│               │   ├── model/
│               │   ├── repository/
│               │   └── service/
│               └── resources/
└── frontend/
    └── hhhg.txt
```

### Notes

- Backend is the actual Spring Boot application.
- Frontend folder is only a placeholder.
- No `src/test` directory exists.

---

# 2. What the Project Does

The project is an **API Change Impact Analyzer** built using **Spring Boot**.

It provides the following functionality:

- Static API extraction using JavaParser
- Generates OpenAPI specifications
- Compares old and new API versions
- Detects breaking API changes
- Calculates API impact score
- Performs security extraction and analysis
- Automatically creates snapshots during startup

The project also contains several design documents describing future enhancements such as:

- Dependency Analyzer
- Blast Radius analysis
- CLI integration
- Git Hook automation

These are design documents only and are not fully implemented.

---

# 3. How the Application Works

## Entry Point

```
ApiciaApplication.java
```

The application starts as a normal Spring Boot application.

---

## Startup Workflow

On startup the application:

1. Extracts endpoints
2. Generates OpenAPI specification
3. Creates snapshot
4. Optionally compares with previous snapshot

---

## REST Controllers

### AnalysisController

Base Path

```
/api/analysis
```

Endpoints

```
POST /api/analysis/compare
GET  /api/analysis/reports
GET  /api/analysis/reports/{id}
```

---

### ExtractionController

Base Path

```
/api/extraction
```

Endpoints

```
GET  /api/extraction/endpoints
GET  /api/extraction/openapi.json
POST /api/extraction/snapshot
```

---

### SpecController

Base Path

```
/api/specs
```

Endpoints

```
POST /api/specs/upload
GET  /api/specs
GET  /api/specs/{id}
```

---

## Core Services

### StaticEndpointExtractionService

Responsible for

- Parsing Java source files
- Detecting controllers
- Reading mapping annotations
- Generating OpenAPI specification

---

### SecurityExtractionService

Responsible for

- Parsing Spring Security configuration
- Detecting JWT
- Detecting OAuth2
- Detecting Basic Authentication
- Detecting authorization annotations

---

### SGMService

Runs rule-based API comparison.

Implemented Rules

- SGM-001 Versioning Rule
- SGM-002 Naming Convention Rule
- SGM-003 Removed Endpoint Rule
- SGM-004 Parameter Type Change Rule
- SGM-005 Required Field Rule
- SGM-006 HTTP Method Change Rule

---

### ImpactScoringService

Calculates

- Structural score
- Total impact score
- Risk level

Risk Levels

- LOW
- MEDIUM
- HIGH
- CRITICAL

---

# 4. Bugs / Code Smells / Findings

## Security

- Database password was committed in git history.
- Password was removed later but still exists in commit history.

---

## Logging

- `GlobalExceptionHandler` uses `System.err.println`.
- Stack traces are discarded.

---

## Exception Handling

- `SGMService` silently ignores exceptions.

---

## Security Extraction

- `SecurityExtractionService` incorrectly identifies every `@Bean` as a SecurityFilterChain.

---

## Dead Code

Unused DTOs

- SAMResultDTO
- SecurityAlertDTO
- SPMResultDTO

---

## Repository Issues

Unused repository methods include

- findAllByOrderByCreatedAtDesc()
- findByOldSpecIdOrNewSpecId()
- findByReportIdAndSeverity()

---

## Configuration Issues

- ObjectMapper overrides Spring Boot default configuration.
- Source root depends on current working directory.
- Versioning rule reports violations on project's own endpoints.
- OAuth URL is hardcoded as example.com.
- Development database configuration is committed.

---

# 5. Tests

Current Status

- No test cases
- No src/test directory
- spring-boot-starter-test dependency exists but is unused

---

# Logging

Only the startup runner uses SLF4J.

Most classes do not perform logging.

---

# Exception Handling

Uses a centralized

```
@RestControllerAdvice
```

However,

- IllegalStateException falls back to generic error handling.
- Silent exception swallowing exists inside SGMService.

---

# Future Features

The repository contains documentation for future enhancements:

- Dependency Analyzer
- Blast Radius Analysis
- CLI Tool
- Automatic Git Integration

These features are currently not implemented.

---

# Overall Assessment

The project successfully implements:

- Static API Extraction
- OpenAPI Generation
- API Version Comparison
- Breaking Change Detection
- Impact Scoring
- Security Extraction

Primary improvements suggested:

- Add automated tests
- Improve logging
- Remove dead code
- Externalize configuration
- Complete planned future features