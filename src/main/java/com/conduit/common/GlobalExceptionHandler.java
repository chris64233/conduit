package com.conduit.common;

import com.conduit.pipesegment.exception.DuplicatePipeSegmentCodeException;
import com.conduit.emergency.exception.BurstEventNotDispatchedException;
import com.conduit.emergency.exception.BurstEventNotFoundException;
import com.conduit.emergency.exception.DuplicateResourceCodeException;
import com.conduit.emergency.exception.EmergencyResourceNotFoundException;
import com.conduit.emergency.exception.InvalidBurstEventStateTransitionException;
import com.conduit.emergency.exception.PipeSegmentNotActiveException;
import com.conduit.emergency.exception.ResourceNotAvailableException;
import com.conduit.hazard.exception.HazardNotFoundException;
import com.conduit.hazard.exception.InspectionTaskNotCompletedException;
import com.conduit.hazard.exception.InvalidHazardStateTransitionException;
import com.conduit.inspection.exception.InspectionTaskNotFoundException;
import com.conduit.inspection.exception.InvalidStateTransitionException;
import com.conduit.pipesegment.exception.InvalidRequestException;
import com.conduit.pipesegment.exception.PipeSegmentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "请求参数不合法" : message);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "请求体格式错误或字段取值不合法");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadParameter(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "请求参数不合法");
    }

    @ExceptionHandler({PipeSegmentNotFoundException.class, InspectionTaskNotFoundException.class,
            HazardNotFoundException.class, BurstEventNotFoundException.class,
            EmergencyResourceNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({DuplicatePipeSegmentCodeException.class, InvalidStateTransitionException.class,
            InspectionTaskNotCompletedException.class, InvalidHazardStateTransitionException.class,
            DuplicateResourceCodeException.class, PipeSegmentNotActiveException.class,
            ResourceNotAvailableException.class, InvalidBurstEventStateTransitionException.class,
            BurstEventNotDispatchedException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
