package com.example.contactscrawler.api;

/**
 * @RestController /answer.
 * GET /answer (page, size, sort, dir, type, domain, q) -> PageResponse<ContactDto>;
 * GET /answer/organizations (page, size, domain, q) -> PageResponse<OrganizationDto>;
 * GET /answer/stats -> StatsResponse. Валидация параметров -> 400. Зависимости: ResultsService, StatsService.
 */
public class AnswerController {
    // TODO: поля и методы см. Javadoc
}
