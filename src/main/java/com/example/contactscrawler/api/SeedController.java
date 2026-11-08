package com.example.contactscrawler.api;

import com.example.contactscrawler.api.dto.SeedsRequest;
import com.example.contactscrawler.crawler.scheduler.SeedService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * /api/seeds: список, добавление (персист — see Q-8), очистка REST-seed'ов.
 */
@RestController
@RequestMapping("/api/seeds")
public class SeedController {

    private final SeedService seedService;

    public SeedController(SeedService seedService) {
        this.seedService = seedService;
    }

    @GetMapping
    public List<String> list() {
        return seedService.all().stream().map(URI::toString).toList();
    }

    @PostMapping
    public List<String> add(@Valid @RequestBody SeedsRequest request) {
        return seedService.add(request.urls()).stream().map(URI::toString).toList();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        seedService.clearApiSeeds();
        return ResponseEntity.noContent().build();
    }
}
