package com.apicia.controller;

import com.apicia.model.dto.AnalysisRequestDTO;
import com.apicia.model.dto.AnalysisResponseDTO;
import com.apicia.model.dto.ReportSummaryDTO;
import com.apicia.service.AnalysisService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "*")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/compare")
    public AnalysisResponseDTO compare(@RequestBody @Valid AnalysisRequestDTO request) {
        return analysisService.compare(request);
    }

    @GetMapping("/reports")
    public List<ReportSummaryDTO> getAllReports() {
        return analysisService.getAllReports();
    }

    @GetMapping("/reports/{id}")
    public AnalysisResponseDTO getReport(@PathVariable("id") Long id) {
        return analysisService.getReport(id);
    }
}
