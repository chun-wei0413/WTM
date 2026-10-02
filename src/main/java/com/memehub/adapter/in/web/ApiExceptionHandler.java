package com.memehub.adapter.in.web;

import com.memehub.application.TemplateNotFoundException;
import com.memehub.application.TooManyRequestsException;
import com.memehub.application.UnsupportedImageException;
import com.memehub.application.auth.InvalidCredentialsException;
import com.memehub.application.generation.GenerationNotFoundException;
import com.memehub.application.generation.MemeNotFoundException;
import com.memehub.application.port.out.ObjectStorageException;
import com.memehub.domain.DomainRuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainRuleViolation.class)
    ProblemDetail domainRule(DomainRuleViolation e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    ProblemDetail notFound(TemplateNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({GenerationNotFoundException.class, MemeNotFoundException.class})
    ProblemDetail generationNotFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnsupportedImageException.class)
    ProblemDetail unsupportedImage(UnsupportedImageException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ProblemDetail> tooManyRequests(TooManyRequestsException e) {
        var response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (e.retryAfter() != null) {
            response = response.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfter().toSeconds()));
        }
        return response.body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail illegalArgument(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ObjectStorageException.class)
    ProblemDetail storageUnavailable(ObjectStorageException e) {
        log.error("Object storage failure", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is unavailable");
    }
}
