package com.apicia.controller;

import com.apicia.exception.ResourceNotFoundException;
import com.apicia.model.entity.ClientDependency;
import com.apicia.model.entity.ClientProject;
import com.apicia.repository.ClientDependencyRepository;
import com.apicia.repository.ClientProjectRepository;
import com.apicia.service.extraction.DependencyScannerService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dependencies")
@CrossOrigin(origins = "*")
public class DependencyController {

    private final DependencyScannerService dependencyScannerService;
    private final ClientProjectRepository clientProjectRepository;
    private final ClientDependencyRepository clientDependencyRepository;

    public DependencyController(
            DependencyScannerService dependencyScannerService,
            ClientProjectRepository clientProjectRepository,
            ClientDependencyRepository clientDependencyRepository
    ) {
        this.dependencyScannerService = dependencyScannerService;
        this.clientProjectRepository = clientProjectRepository;
        this.clientDependencyRepository = clientDependencyRepository;
    }

    @PostMapping("/register")
    public ClientProject register(
            @RequestParam("projectName") String projectName,
            @RequestParam("sourcePath") String sourcePath
    ) {
        return dependencyScannerService.registerAndScan(projectName, sourcePath);
    }

    @PostMapping("/scan/{id}")
    public ClientProject scan(@PathVariable("id") Long id) {
        ClientProject project = clientProjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClientProject not found with id: " + id));
        dependencyScannerService.scanProject(project);
        return project;
    }

    @GetMapping("/projects")
    public List<ClientProject> getProjects() {
        return clientProjectRepository.findAll();
    }

    @GetMapping
    public List<ClientDependency> getDependencies() {
        return clientDependencyRepository.findAll();
    }
}
