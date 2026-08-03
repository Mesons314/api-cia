# Universal Integration Blueprint: Automated API Change Detection

This guide details how to transform `api-cia` from a tool requiring manual YAML uploads into a **universal, automated platform** that integrates with any project, detects changes on git commit, and handles all HTTP methods (`GET`, `POST`, `PUT`, `DELETE`) in a single unified way.

---

## 1. Do We Need Separate Contracts for GET, POST, PUT, DELETE?

**No.** 

You use the **OpenAPI (Swagger) Specification standard**, which represents **all HTTP methods and paths in a single JSON or YAML file**. 

For example, a single `openapi.json` contract defines all your routes:
```json
{
  "paths": {
    "/api/users": {
      "get": { "summary": "Get all users" },
      "post": { "summary": "Create a user" }
    },
    "/api/users/{id}": {
      "put": { "summary": "Update user" },
      "delete": { "summary": "Delete user" }
    }
  }
}
```
Because your existing project already uses `io.swagger.parser.OpenAPIParser`, it can read this single file, parse all verbs, and compare them. You do not need to create anything separately.

---

## 2. The Universal "Git Commit & Compare" Pipeline

To make this work automatically on commit for **any project**, the pipeline uses Git history to build two versions of the API contract dynamically.

```mermaid
graph TD
    subgraph Local Git Repository (Any Project)
        Commit[Developer Commits Code] -->|Hook Triggers| Hook[api-cia Commit Hook]
    end

    subgraph CI / Local CLI Executor
        Hook -->|1. Generate| NewSpec[New OpenAPI Spec (Current HEAD)]
        Hook -->|2. Git Checkout HEAD~1| Temp[Temp Previous Codebase]
        Temp -->|3. Generate| OldSpec[Old OpenAPI Spec (Previous Commit)]
        Hook -->|4. Upload Specs| API[api-cia Server]
    end

    subgraph api-cia Engine
        API -->|Diff Specs| Diff[SGM Service Comparison]
        Diff -->|Output| Report[Change & Blast Radius Report]
    end
```

### The 4-Step Pipeline:
1. **Trigger**: The developer runs `git commit` or `git push`. A Git hook (e.g. `pre-push`) triggers a lightweight runner script.
2. **Generate New Spec (HEAD)**: The runner script calls the project's native build command to output the current API contract (`openapi-new.json`).
3. **Generate Old Spec (BASE)**: The runner checks out the code from the previous commit (`HEAD~1` or the target branch `main`), compiles/generates the spec (`openapi-old.json`).
4. **Compare**: The runner uploads both files to your `api-cia` backend, which runs the comparison and returns the report.

---

## 3. How to Make It Compatible with "Any Project"

To support any language (Java, Python, Node.js, Go), you do not write parser code for every language. Instead, you rely on the **framework's native OpenAPI generator tools**.

Your integration script simply checks the project type and runs the corresponding command:

### A. For Java / Spring Boot Projects
* **Native Tool**: `springdoc-openapi-maven-plugin`
* **Auto-Generation Command**:
  ```bash
  ./mvnw springdoc-openapi:run
  # Generates target/openapi.json automatically
  ```

### B. For Node.js / Express Projects
* **Native Tool**: `swagger-jsdoc` or `tsoa`
* **Auto-Generation Command**:
  ```bash
  npx tsoa spec
  # Generates dist/swagger.json automatically
  ```

### C. For Python / FastAPI Projects
* **Native Tool**: Built-in FastAPI OpenAPI generator
* **Auto-Generation Command**:
  ```bash
  python -c "import json; from main import app; print(json.dumps(app.openapi()))" > openapi.json
  ```

---

## 4. The Integration Bridge: The `api-cia` CLI

To make integration simple, you can build a small CLI tool (e.g. written in Node/JavaScript, compileable to a binary, or a simple shell script) that developers drop into any repository.

### Developer Setup (Only done once):
The developer runs:
```bash
npx api-cia-cli init
```
This generates a config file `.api-cia.json` in their project:
```json
{
  "projectId": "inventory-service",
  "serverUrl": "https://your-api-cia.com",
  "generationCommand": "./mvnw springdoc-openapi:run"
}
```

### Git Hook Integration:
Inside the `.git/hooks/pre-push` file:
```bash
#!/bin/sh
# Generate specs and verify before pushing
npx api-cia-cli analyze
```

If the CLI runs `api-cia-cli analyze` and your backend returns a **CRITICAL/BREAKING** change that impacts downstream clients, the hook can exit with a non-zero code, preventing the developer from pushing broken code!
