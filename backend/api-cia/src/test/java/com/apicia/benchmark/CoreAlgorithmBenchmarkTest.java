package com.apicia.benchmark;

import com.apicia.repository.ClientDependencyRepository;
import com.apicia.repository.ClientProjectRepository;
import com.apicia.service.extraction.DependencyScannerService;
import com.apicia.service.scoring.ImpactScoringService;
import com.apicia.util.VersionComparator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

public class CoreAlgorithmBenchmarkTest {

    @Test
    public void runCoreAlgorithmMicroBenchmarks() throws Exception {
        ClientProjectRepository projectRepository = Mockito.mock(ClientProjectRepository.class);
        ClientDependencyRepository dependencyRepository = Mockito.mock(ClientDependencyRepository.class);
        DependencyScannerService scanner = new DependencyScannerService(projectRepository, dependencyRepository);
        ImpactScoringService scoringService = new ImpactScoringService();

        // Inject default values via reflection since Spring context is not loaded in pure unit test
        setField(scoringService, "w1", 0.70);
        setField(scoringService, "w2", 0.30);
        setField(scoringService, "maxConsumers", 10);
        setField(scoringService, "thresholdMedium", 0.25);
        setField(scoringService, "thresholdHigh", 0.50);
        setField(scoringService, "thresholdCritical", 0.75);

        String samplePath = "http://localhost:8080/api/v1/users/{userId}/orders/123";
        String v1 = "1.2.3-SNAPSHOT";
        String v2 = "1.2.3";
        double dStruct = 0.65;
        int blastRadius = 3;

        System.out.println("\n=========================================================================================================");
        System.out.printf(Locale.US, "%-35s | %-10s | %-12s | %-14s | %-15s%n",
                "Function Under Test", "n (calls)", "Total Time", "Avg Latency", "Throughput");
        System.out.println("---------------------------------------------------------------------------------------------------------");

        // 1. normalizePath
        benchmark("normalizePath()", 1_000, () -> {
            scanner.normalizePath(samplePath);
        });
        benchmark("normalizePath()", 10_000, () -> {
            scanner.normalizePath(samplePath);
        });
        benchmark("normalizePath()", 100_000, () -> {
            scanner.normalizePath(samplePath);
        });

        // 2. compareVersions
        benchmark("compareVersions()", 1_000, () -> {
            VersionComparator.compareVersions(v1, v2);
        });
        benchmark("compareVersions()", 10_000, () -> {
            VersionComparator.compareVersions(v1, v2);
        });
        benchmark("compareVersions()", 100_000, () -> {
            VersionComparator.compareVersions(v1, v2);
        });

        // 3. ImpactScoringService.calculate()
        benchmark("ImpactScoringService.calculate()", 1_000, () -> {
            scoringService.calculate(dStruct, blastRadius);
        });
        benchmark("ImpactScoringService.calculate()", 100_000, () -> {
            scoringService.calculate(dStruct, blastRadius);
        });

        System.out.println("=========================================================================================================\n");
    }

    @Test
    public void runRealProjectPipelineBenchmark() throws Exception {
        System.out.println("\n=========================================================================================================");
        System.out.printf(Locale.US, "%-40s | %-12s | %-12s | %-20s%n",
                "APICIA Real Project Pipeline Phase", "Dataset / Size", "Execution Time", "Throughput / Details");
        System.out.println("---------------------------------------------------------------------------------------------------------");

        // 1. Spec Comparison & 12 Rules Evaluation
        String v1Json = java.nio.file.Files.readString(java.nio.file.Path.of("samples/openapi_v1.json"));
        String v2Json = java.nio.file.Files.readString(java.nio.file.Path.of("samples/openapi_v2.json"));
        io.swagger.parser.OpenAPIParser parser = new io.swagger.parser.OpenAPIParser();
        io.swagger.v3.oas.models.OpenAPI spec1 = parser.readContents(v1Json, null, null).getOpenAPI();
        io.swagger.v3.oas.models.OpenAPI spec2 = parser.readContents(v2Json, null, null).getOpenAPI();

        List<com.apicia.service.sgm.rules.DesignRule> rules = List.of(
                new com.apicia.service.sgm.rules.VersioningRule(),
                new com.apicia.service.sgm.rules.NamingConventionRule(),
                new com.apicia.service.sgm.rules.RemovedEndpointRule(),
                new com.apicia.service.sgm.rules.ParameterTypeChangeRule(),
                new com.apicia.service.sgm.rules.RequiredFieldRule(),
                new com.apicia.service.sgm.rules.HttpMethodChangeRule(),
                new com.apicia.service.sgm.rules.SecurityViolationRule(),
                new com.apicia.service.sgm.rules.EndpointRenameRule(),
                new com.apicia.service.sgm.rules.RestPathStylingRule(),
                new com.apicia.service.sgm.rules.VerbInPathRule(),
                new com.apicia.service.sgm.rules.ConsistentErrorResponseRule(),
                new com.apicia.service.sgm.rules.ResponseTypeChangeRule()
        );
        com.apicia.service.sgm.SGMService sgmService = new com.apicia.service.sgm.SGMService(rules);

        // Warm-up
        sgmService.analyze(spec1, spec2);

        long t0 = System.nanoTime();
        com.apicia.model.dto.SGMResultDTO sgmResult = sgmService.analyze(spec1, spec2);
        double sgmTimeMs = (System.nanoTime() - t0) / 1_000_000.0;
        System.out.printf(Locale.US, "%-40s | %-12s | %9.3f ms | %d violations detected%n",
                "12 SGM Rules Spec Diff", "v1 vs v2 (11 ops)", sgmTimeMs, sgmResult.getTotalViolations());

        // 2. Static Endpoint Extraction on Real Codebase
        com.apicia.config.EndpointExtractionProperties props = new com.apicia.config.EndpointExtractionProperties();
        props.setSourceRoot("src/main/java");
        props.setTitle("APICIA API");
        props.setVersion("1.0.0");
        props.setServers(List.of("http://localhost:8080"));
        props.setIncludeExtractorEndpoints(true);

        com.apicia.service.extraction.SecurityExtractionService secExtractionService = new com.apicia.service.extraction.SecurityExtractionService();
        com.apicia.service.extraction.StaticEndpointExtractionService extractionService = new com.apicia.service.extraction.StaticEndpointExtractionService(props, secExtractionService);

        t0 = System.nanoTime();
        var extractedEndpoints = extractionService.extractEndpoints("src/main/java");
        double extractionTimeMs = (System.nanoTime() - t0) / 1_000_000.0;
        System.out.printf(Locale.US, "%-40s | %-12s | %9.3f ms | %d endpoints extracted%n",
                "Static Endpoint Extraction", "71 Java files", extractionTimeMs, extractedEndpoints.size());

        // 3. Security & Compliance Scan on Real Codebase
        com.apicia.service.security.SecurityComplianceScannerService secScanner = new com.apicia.service.security.SecurityComplianceScannerService(extractionService, secExtractionService, props);
        t0 = System.nanoTime();
        var samResult = secScanner.auditSourceCode("src/main/java");
        double secAuditTimeMs = (System.nanoTime() - t0) / 1_000_000.0;
        System.out.printf(Locale.US, "%-40s | %-12s | %9.3f ms | %d security alerts raised%n",
                "Security & Compliance Audit", "71 Java files", secAuditTimeMs, samResult.getTotalAlerts());

        // 4. Postman Collection Export
        com.apicia.service.sdk.SdkGeneratorService sdkService = new com.apicia.service.sdk.SdkGeneratorService();
        t0 = System.nanoTime();
        sdkService.exportPostmanCollection(v2Json);
        double postmanTimeMs = (System.nanoTime() - t0) / 1_000_000.0;
        System.out.printf(Locale.US, "%-40s | %-12s | %9.3f ms | Postman Collection v2.1.0%n",
                "Postman Export", "6 operations", postmanTimeMs);

        // 5. TypeScript Axios SDK Generation
        t0 = System.nanoTime();
        byte[] sdkZip = sdkService.generateSdk(v2Json, "typescript-axios");
        double sdkTimeMs = (System.nanoTime() - t0) / 1_000_000.0;
        System.out.printf(Locale.US, "%-40s | %-12s | %9.3f ms | ZIP size: %,d bytes%n",
                "SDK: typescript-axios", "6 operations", sdkTimeMs, sdkZip.length);

        System.out.println("=========================================================================================================\n");
    }

    private void benchmark(String name, int n, Runnable task) {
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            task.run();
        }
        long durationNs = System.nanoTime() - start;

        double totalTimeMs = durationNs / 1_000_000.0;
        double avgLatencyUs = (durationNs / (double) n) / 1_000.0;
        double throughputOpsPerSec = (n / (double) durationNs) * 1_000_000_000.0;

        String throughputFormatted;
        if (throughputOpsPerSec >= 1_000_000) {
            throughputFormatted = String.format(Locale.US, "%.2fM ops/s", throughputOpsPerSec / 1_000_000.0);
        } else {
            throughputFormatted = String.format(Locale.US, "%,.0f ops/s", throughputOpsPerSec);
        }

        System.out.printf(Locale.US, "%-35s | %,10d | %9.3f ms | %10.3f µs | %15s%n",
                name, n, totalTimeMs, avgLatencyUs, throughputFormatted);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
