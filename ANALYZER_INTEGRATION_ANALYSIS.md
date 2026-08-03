# Analyzer Integration Analysis

**Objective**

Analyze how the current project sends the Base API Contract and the Input API Contract to the analyzer in order to generate an Analysis Report.

---

## 1. Where is the Base API Contract stored/generated?

There is no dedicated "base contract" concept in the code — `SpecVersion` (`backend/api-cia/src/main/java/com/apicia/model/entity/SpecVersion.java:22-43`) has no `isBase`/`isReference` flag.

A contract becomes "old/base" purely by virtue of which ID the caller passes as `oldSpecId`.

It gets into the database in one of two ways:

### Manual Upload

`SpecController.upload()` (`SpecController.java:29-65`)

- Saves it into the `spec_versions` table using `SpecVersionRepository.save()`.

### Self-generated Snapshot

`EndpointExtractionWorkflowService.extractSaveAndAnalyze()` (`EndpointExtractionWorkflowService.java:61-105`)

Triggered by

`EndpointExtractionStartupRunner.extractOnStartup()`

(`EndpointExtractionStartupRunner.java:27-46`)

The previous snapshot is resolved using

```java
specVersionRepository.findFirstByFileNameOrderByUploadedAtDesc(...)
```

This is the only automatic "base contract" mechanism.

---

## 2. Where does the Input API Contract enter?

Endpoint

```
POST /api/specs/upload
```

Controller

```
SpecController.java
```

Flow

- Multipart File
- Read as bytes
- Parsed using Swagger Parser
- Stored inside `SpecVersion.rawContent`

The parsed `OpenAPI` object is discarded after parsing.

---

## 3. Which controller receives these contracts?

### SpecController

Receives both uploaded contracts.

There is no dedicated Base/Input endpoint.

### AnalysisController

Receives only

```text
oldSpecId
newSpecId
```

inside

```
AnalysisRequestDTO
```

---

## 4. Which service compares them?

```
AnalysisService.compare()
```

↓

```
SGMService.analyze()
```

The analyzer executes all six `DesignRule` implementations.

---

## 5. How are both contracts converted?

Inside

```
AnalysisService.compare()
```

```java
OpenAPI oldAPI =
new OpenAPIParser().readContents(oldSpec.getRawContent()).getOpenAPI();

OpenAPI newAPI =
new OpenAPIParser().readContents(newSpec.getRawContent()).getOpenAPI();
```

Each stored contract is parsed again into an `OpenAPI` object before analysis.

---

## 6. Which method invokes the analyzer?

```java
SGMResultDTO result =
sgmService.analyze(oldAPI, newAPI);
```

Located in

```
AnalysisService.compare()
```

---

## 7. Which method generates the Analysis Report?

Still inside

```
AnalysisService.compare()
```

Flow

- Save `AnalysisReport`
- Save all `Violation` objects
- Return `AnalysisResponseDTO`

Reports are later retrieved through

```
GET /api/analysis/reports/{id}
```

---

# 8. What's Missing?

Current flow works correctly but requires **three API calls**:

1. Upload Base Contract

```
POST /api/specs/upload
```

↓

2. Upload Input Contract

```
POST /api/specs/upload
```

↓

3. Compare

```
POST /api/analysis/compare
```

There is currently:

- No single endpoint that accepts both contracts.
- No "current baseline" concept.
- No stateless compare endpoint using raw contract content.

---

# 9. Suggested Changes

## Baseline Approach

### SpecVersion.java

Add

```java
boolean isBaseline;
```

### SpecVersionRepository.java

Add

```java
findByIsBaselineTrue()
```

### SpecController.java

Add endpoint

```
PUT /api/specs/{id}/baseline
```

### AnalysisService.java

Add

```
compareAgainstBaseline(MultipartFile input)
```

### AnalysisController.java

Add

```
POST /api/analysis/compare-against-baseline
```

---

## Alternative Approach

Implement

```
compareRaw(oldContent, newContent)
```

Accept two raw contracts without saving them first.

---

# 10. Current Request Flow

```
User Uploads Base Contract
        ↓
POST /api/specs/upload
        ↓
SpecController.upload()
        ↓
SpecVersionRepository.save()
        ↓

User Uploads Input Contract
        ↓
POST /api/specs/upload
        ↓
SpecVersionRepository.save()
        ↓

POST /api/analysis/compare
(oldSpecId, newSpecId)
        ↓
AnalysisController.compare()
        ↓
AnalysisService.compare()
        ↓
OpenAPIParser
        ↓
SGMService.analyze()
        ↓
ImpactScoringService.calculate()
        ↓
AnalysisReport + Violations saved
        ↓
AnalysisResponseDTO returned
```