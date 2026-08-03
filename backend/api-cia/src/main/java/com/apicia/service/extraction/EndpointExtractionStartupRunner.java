package com.apicia.service.extraction;

import com.apicia.config.EndpointExtractionProperties;
import com.apicia.model.dto.ExtractionSnapshotDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EndpointExtractionStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(EndpointExtractionStartupRunner.class);

    private final EndpointExtractionProperties properties;
    private final EndpointExtractionWorkflowService workflowService;

    public EndpointExtractionStartupRunner(
            EndpointExtractionProperties properties,
            EndpointExtractionWorkflowService workflowService
    ) {
        this.properties = properties;
        this.workflowService = workflowService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void extractOnStartup() {
        if (!properties.isEnabled() || !properties.isStartupEnabled()) {
            return;
        }
        try {
            ExtractionSnapshotDTO snapshot = workflowService.extractSaveAndAnalyze(properties.isAnalyzeOnStartup());
            log.info("Static endpoint extraction completed: specId={}, endpoints={}, saved={}, analyzed={}, reportId={}",
                    snapshot.getSpecId(),
                    snapshot.getTotalEndpoints(),
                    snapshot.isSaved(),
                    snapshot.isAnalyzed(),
                    snapshot.getAnalysisReportId());
        } catch (RuntimeException ex) {
            if (properties.isFailOnStartupError()) {
                throw ex;
            }
            log.warn("Static endpoint extraction failed during startup", ex);
        }
    }
}
