package com.example.contactscrawler.query;

/**
 * Выборка для /answer и /answer/organizations: ContactRepository.findByFilter(type, domain) (сужение в хранилище) ->
 * parallelStream().filter(q).sorted(comparator по sort/dir) -> skip/limit -> PageResponse<ContactDto>.
 * organizations(): groupingByConcurrent по domain -> OrganizationDto. Без побочных эффектов в лямбдах.
 */
public class ResultsService {
    // TODO: поля и методы см. Javadoc
}
