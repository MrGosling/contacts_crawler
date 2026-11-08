package com.example.contactscrawler.api;

import com.example.contactscrawler.api.dto.CrawlJobResponse;
import com.example.contactscrawler.api.dto.CrawlRequest;
import com.example.contactscrawler.api.dto.CrawlerStatusResponse;
import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.crawler.CrawlerService;
import com.example.contactscrawler.crawler.model.CrawlJob;
import com.example.contactscrawler.crawler.model.CrawlParams;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * /api/crawl: запуск (202 + Location), статус задания, отмена, общий статус краулера.
 */
@RestController
@RequestMapping("/api/crawl")
public class CrawlController {

    private final CrawlerService crawlerService;
    private final CrawlerProperties props;

    public CrawlController(CrawlerService crawlerService, CrawlerProperties props) {
        this.crawlerService = crawlerService;
        this.props = props;
    }

    @PostMapping
    public ResponseEntity<CrawlJobResponse> start(@Valid @RequestBody CrawlRequest request) {
        CrawlerService.Submitted submitted = crawlerService.submitAndDescribe(CrawlParams.of(request, props));
        return ResponseEntity.accepted()
                .location(URI.create("/api/crawl/" + submitted.job().id()))
                .body(submitted.snapshot());
    }

    @GetMapping("/status")
    public CrawlerStatusResponse status() {
        return crawlerService.status();
    }

    @GetMapping("/{jobId}")
    public CrawlJobResponse get(@PathVariable String jobId) {
        return crawlerService.jobResponse(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<CrawlJobResponse> cancel(@PathVariable String jobId) {
        CrawlJob job = crawlerService.find(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        if (!crawlerService.cancel(jobId) && !job.isCancelled()) {
            throw new IllegalStateException("Job " + jobId + " is already " + job.status());
        }
        return ResponseEntity.accepted().body(job.toResponse());
    }
}
