package com.example.contactscrawler.api;

import com.example.contactscrawler.config.CrawlerProperties;
import com.example.contactscrawler.storage.ContactRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DELETE /api/data → 204, или 403 при crawler.api.allow-delete=false (D-12).
 */
@RestController
public class DataController {

    private final ContactRepository repository;
    private final CrawlerProperties props;

    public DataController(ContactRepository repository, CrawlerProperties props) {
        this.repository = repository;
        this.props = props;
    }

    @DeleteMapping("/api/data")
    public ResponseEntity<Void> deleteAll() {
        if (!props.getApi().isAllowDelete()) {
            throw new OperationForbiddenException("Data deletion is disabled (crawler.api.allow-delete=false)");
        }
        repository.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
