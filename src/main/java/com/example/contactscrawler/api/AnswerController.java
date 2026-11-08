package com.example.contactscrawler.api;

import com.example.contactscrawler.api.dto.ContactDto;
import com.example.contactscrawler.api.dto.OrganizationDto;
import com.example.contactscrawler.api.dto.PageResponse;
import com.example.contactscrawler.api.dto.StatsResponse;
import com.example.contactscrawler.query.ContactQuery;
import com.example.contactscrawler.query.ResultsService;
import com.example.contactscrawler.query.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /answer — результаты с пагинацией, сортировкой и фильтрацией; /answer/organizations; /answer/stats.
 * Невалидные page/size/sort/dir/type → 400.
 */
@RestController
@RequestMapping("/answer")
public class AnswerController {

    private final ResultsService resultsService;
    private final StatsService statsService;

    public AnswerController(ResultsService resultsService, StatsService statsService) {
        this.resultsService = resultsService;
        this.statsService = statsService;
    }

    @GetMapping
    public PageResponse<ContactDto> answer(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(defaultValue = "foundAt") String sort,
                                           @RequestParam(defaultValue = "desc") String dir,
                                           @RequestParam(required = false) String type,
                                           @RequestParam(required = false) String domain,
                                           @RequestParam(required = false) String q) {
        return resultsService.search(ContactQuery.of(page, size, sort, dir, type, domain, q));
    }

    @GetMapping("/organizations")
    public PageResponse<OrganizationDto> organizations(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @RequestParam(required = false) String domain,
                                                       @RequestParam(required = false) String q) {
        return resultsService.organizations(page, size, domain, q);
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return statsService.stats();
    }
}
