package com.example.contactscrawler.api;

/**
 * @RestControllerAdvice. Преобразует исключения в ProblemDetail: валидация -> 400, JobNotFoundException -> 404,
 * OperationForbiddenException -> 403, прочее -> 500.
 */
public class ApiExceptionHandler {
    // TODO: поля и методы см. Javadoc
}
