package com.example.activity.api;

import com.example.activity.domain.InvalidActivityException;
import com.example.activity.ingestion.UpstreamException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidActivityException.class)
    public ProblemDetail invalid(InvalidActivityException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); }
    @ExceptionHandler(UpstreamException.class)
    public ProblemDetail upstream(UpstreamException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A valid request body is required"); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge(MaxUploadSizeExceededException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Upload exceeds size limit"); }
}
