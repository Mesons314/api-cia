package com.apicia.service.extraction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.apicia.model.entity.ClientDependency;
import com.apicia.model.entity.ClientProject;
import com.apicia.repository.ClientDependencyRepository;
import com.apicia.repository.ClientProjectRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

public class DependencyScannerServiceTest {

    private ClientProjectRepository projectRepository;
    private ClientDependencyRepository dependencyRepository;
    private DependencyScannerService scannerService;

    @BeforeEach
    public void setUp() {
        projectRepository = mock(ClientProjectRepository.class);
        dependencyRepository = mock(ClientDependencyRepository.class);
        scannerService = new DependencyScannerService(projectRepository, dependencyRepository);
    }

    @Test
    public void testNormalizePath() {
        assertEquals("/api/v1/users/{}", scannerService.normalizePath("/api/v1/users/{id}"));
        assertEquals("/api/v1/users/{}", scannerService.normalizePath("/api/v1/users/{userId}"));
        assertEquals("/api/v1/users/{}", scannerService.normalizePath("http://localhost:8080/api/v1/users/123"));
        assertEquals("/api/v1/users/{}", scannerService.normalizePath("/{}/api/v1/users/{}"));
    }

    @Test
    public void testScanProject(@TempDir Path tempDir) throws IOException {
        String feignCode = "package com.example.client;\n" +
                "import org.springframework.cloud.openfeign.FeignClient;\n" +
                "import org.springframework.web.bind.annotation.*;\n" +
                "@FeignClient(name = \"user-service\", path = \"/api/v1/users\")\n" +
                "public interface UserClient {\n" +
                "    @GetMapping(\"/{id}\")\n" +
                "    String getUser(@PathVariable(\"id\") String id);\n" +
                "    @PostMapping\n" +
                "    String createUser(@RequestBody String user);\n" +
                "}\n";

        String restTemplateCode = "package com.example.service;\n" +
                "import org.springframework.web.client.RestTemplate;\n" +
                "public class MyService {\n" +
                "    private RestTemplate restTemplate;\n" +
                "    public void doCall() {\n" +
                "        String url = \"http://localhost:8080/api/v1/orders/\" + 456;\n" +
                "        restTemplate.getForObject(url, String.class);\n" +
                "    }\n" +
                "}\n";

        String webClientCode = "package com.example.service;\n" +
                "import org.springframework.web.reactive.function.client.WebClient;\n" +
                "public class WebService {\n" +
                "    private WebClient webClient;\n" +
                "    public void doCall() {\n" +
                "        webClient.post().uri(\"/api/v1/payments/{paymentId}\").retrieve().bodyToMono(String.class);\n" +
                "    }\n" +
                "}\n";

        Files.writeString(tempDir.resolve("UserClient.java"), feignCode);
        Files.writeString(tempDir.resolve("MyService.java"), restTemplateCode);
        Files.writeString(tempDir.resolve("WebService.java"), webClientCode);

        ClientProject project = ClientProject.builder()
                .id(1L)
                .projectName("TestClient")
                .sourcePath(tempDir.toString())
                .build();

        scannerService.scanProject(project);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClientDependency>> captor = ArgumentCaptor.forClass(List.class);
        verify(dependencyRepository).saveAll(captor.capture());

        List<ClientDependency> saved = captor.getValue();
        assertNotNull(saved);
        assertEquals(4, saved.size());

        ClientDependency getFeign = saved.stream()
                .filter(d -> d.getFilePath().endsWith("UserClient.java") && "GET".equals(d.getHttpMethod()))
                .findFirst().orElse(null);
        assertNotNull(getFeign);
        assertEquals("/api/v1/users/{}", getFeign.getNormalizedPath());

        ClientDependency postFeign = saved.stream()
                .filter(d -> d.getFilePath().endsWith("UserClient.java") && "POST".equals(d.getHttpMethod()))
                .findFirst().orElse(null);
        assertNotNull(postFeign);
        assertEquals("/api/v1/users", postFeign.getNormalizedPath());

        ClientDependency rest = saved.stream()
                .filter(d -> d.getFilePath().endsWith("MyService.java"))
                .findFirst().orElse(null);
        assertNotNull(rest);
        assertEquals("GET", rest.getHttpMethod());
        assertEquals("/api/v1/orders/{}", rest.getNormalizedPath());

        ClientDependency web = saved.stream()
                .filter(d -> d.getFilePath().endsWith("WebService.java"))
                .findFirst().orElse(null);
        assertNotNull(web);
        assertEquals("POST", web.getHttpMethod());
        assertEquals("/api/v1/payments/{}", web.getNormalizedPath());
    }
}
