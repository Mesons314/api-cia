# Code Modification Guide: Building Dependency Analysis & Blast Radius

This guide details the exact packages, files, and modifications you need to make in your `api-cia` codebase.

---

## 1. Modify Build Configuration: `pom.xml`

Open [pom.xml](file:///c:/Apicia/backend/api-cia/pom.xml) and add the JavaParser dependency to the `<dependencies>` section:

* **What to add**:
  * `com.github.javaparser:javaparser-core` (Version `3.26.1` recommended).
  * (Optional but recommended for resolving types across files): `com.github.javaparser:javaparser-symbol-solver-core`.

---

## 2. Database & Data Modeling: New Entities & Repositories

You need to save information about registered client projects and their API dependencies.

### Create Entities in `com.apicia.model.entity`
Create two new classes:
1. **`ClientProject.java`**:
   - **Fields**: `id` (Long, `@Id`), `name` (String), `gitUrl` / `localPath` (String), `createdAt` (LocalDateTime).
   - **Relationship**: `@OneToMany` mapping to `ClientDependency`.
2. **`ClientDependency.java`**:
   - **Fields**: `id` (Long, `@Id`), `filePath` (String), `lineNumber` (int), `httpMethod` (String), `normalizedPath` (String).
   - **Relationship**: `@ManyToOne` mapping back to `ClientProject`.

### Create Repositories in `com.apicia.repository`
Create the corresponding Spring Data JPA interfaces:
1. **`ClientProjectRepository.java`** extending `JpaRepository<ClientProject, Long>`.
2. **`ClientDependencyRepository.java`** extending `JpaRepository<ClientDependency, Long>`:
   - Add a query method: `List<ClientDependency> findByNormalizedPathAndHttpMethod(String path, String method);`

---

## 3. Create the Static Analysis Parser: `com.apicia.service`

Create a new service class: **`DependencyScannerService.java`**

### Step A: Codebase File Traversal
Implement a method that walks a directory (using `Files.walk` or similar Java NIO utilities) to find all files ending with `.java`.

### Step B: AST Parsing Visitor
Implement an AST visitor class extending JavaParser's `VoidVisitorAdapter<Void>` to inspect files:
1. **Feign Client Parser**:
   - Override `visit(ClassOrInterfaceDeclaration n, Void arg)`.
   - Check if annotation `@FeignClient` is present.
   - If present, extract the client name and inspect method definitions inside the interface for mapping annotations (`@GetMapping`, etc.).
2. **RestTemplate / WebClient Parser**:
   - Override `visit(MethodCallExpr n, Void arg)`.
   - Match calls where the method identifier is one of the target HTTP client methods (`uri`, `getForObject`, `exchange`).
   - Extract string literals or expression chains to get the relative URL.

---

## 4. Integrate Blast Radius into `AnalysisService.java`

Open [AnalysisService.java](file:///c:/Apicia/backend/api-cia/src/main/java/com/apicia/service/AnalysisService.java) and modify the `compare(...)` and `getReport(...)` flow to compute the blast radius.

### Step A: Inject the new Repository
Add a reference to `ClientDependencyRepository` in the constructor.

### Step B: Enrich the Response DTO
1. In `com.apicia.model.dto.AnalysisResponseDTO`, add a new field (e.g. `Map<String, List<ClientDependencyDTO>> blastRadius` or a dedicated `BlastRadiusDTO`).
2. After calling `sgmService.analyze(oldAPI, newAPI)` and storing violations, iterate over any violating endpoints of type `BREAKING`.
3. For each broken endpoint (HTTP Method + Normalized Path), call:
   ```java
   List<ClientDependency> clients = clientDependencyRepository.findByNormalizedPathAndHttpMethod(path, method);
   ```
4. Map these matching database entities to your DTOs and group them by client project, then set them on the response DTO.

---

## 5. Expose REST APIs for Client Integration: `com.apicia.controller`

You need endpoints for:
1. Registering/scanning a client project.
2. Automating endpoint extraction on the provider project (Strategy A - zero manual uploads).

### Option A: Create `ClientController.java`
Add a controller to register client applications:
* **Endpoint**: `POST /api/clients/register`
  - **Payload**: `ClientRegisterRequestDTO` containing project `name` and `localPath`.
  - **Logic**: Calls `DependencyScannerService` to walk the code, parse HTTP calls, and persist them in the database associated with the client.

### Option B: Extend `SpecController.java` to support Direct Code Scanning
If you want to avoid uploading YAML files entirely for provider projects:
1. Open [SpecController.java](file:///c:/Apicia/backend/api-cia/src/main/java/com/apicia/controller/SpecController.java).
2. Add a new endpoint `POST /api/specs/scan-provider`.
   - **Payload**: Request containing the path to a provider's Java project.
   - **Logic**: Use JavaParser on the provider codebase to scan `@RestController` classes and their routing methods to synthesize the `OpenAPI` spec directly in memory.
   - Convert this virtual specification to raw JSON string, and save it as a new `SpecVersion` automatically!
