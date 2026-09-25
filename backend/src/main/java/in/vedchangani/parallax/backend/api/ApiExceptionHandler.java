package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.dataset.DatasetIntegrityException;
import in.vedchangani.parallax.backend.dataset.DatasetNotFoundException;
import in.vedchangani.parallax.backend.dataset.DatasetVersionConflictException;
import in.vedchangani.parallax.backend.dataset.DatasetVersionNotFoundException;
import in.vedchangani.parallax.backend.dataset.DuplicateDatasetNameException;
import in.vedchangani.parallax.backend.dataset.csv.InvalidCsvDataException;
import in.vedchangani.parallax.backend.dataset.csv.MalformedCsvException;
import in.vedchangani.parallax.backend.strategy.DuplicateStrategyNameException;
import in.vedchangani.parallax.backend.strategy.StrategyNotFoundException;
import in.vedchangani.parallax.backend.strategy.StrategyVersionConflictException;
import in.vedchangani.parallax.backend.strategy.StrategyVersionNotFoundException;
import in.vedchangani.parallax.backend.strategy.definition.InvalidStrategyDefinitionException;
import in.vedchangani.parallax.backend.strategy.definition.MalformedStrategyDefinitionException;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionIntegrityException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * The D-31/D-32 REST error boundary (D-31 §11, D-32 §20). Every domain
 * exception maps to a fixed {@link ProblemDetail} — never an engine/Jackson
 * exception class name, stack trace, SQL, or database constraint name.
 * Spring MVC's own framework-level failures (unreadable/missing body,
 * path-variable type mismatch, unsupported media type, method not allowed)
 * are left to the inherited {@link ResponseEntityExceptionHandler}
 * defaults; nothing here duplicates them.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Malformed strategy-definition JSON (D-30 layer 1/2 shape failure) → 400. */
    @ExceptionHandler(MalformedStrategyDefinitionException.class)
    public ProblemDetail handleMalformed(MalformedStrategyDefinitionException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Malformed request");
        problem.setProperty("field", e.path());
        return problem;
    }

    /** Malformed CSV syntax (D-32 §3/§4: encoding, header, field grammar, overflow) → 400. */
    @ExceptionHandler(MalformedCsvException.class)
    public ProblemDetail handleMalformedCsv(MalformedCsvException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Malformed request");
        problem.setProperty("line", e.line());
        return problem;
    }

    /** Malformed dataset-version upload request (missing/extra multipart part, bad filename) → 400. */
    @ExceptionHandler(MalformedDatasetUploadException.class)
    public ProblemDetail handleMalformedDatasetUpload(MalformedDatasetUploadException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Malformed request");
        problem.setProperty("field", e.field());
        return problem;
    }

    /** Envelope Bean Validation failure (blank/over-long name or description) → 400. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "request failed validation");
        problem.setTitle("Malformed request");
        List<Map<String, String>> errors = e.getConstraintViolations().stream()
                .map(ApiExceptionHandler::toFieldError)
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> toFieldError(ConstraintViolation<?> violation) {
        return Map.of("field", violation.getPropertyPath().toString(), "message", violation.getMessage());
    }

    /** Semantically invalid strategy definition (D-30 layer 3, engine semantics) → 422. */
    @ExceptionHandler(InvalidStrategyDefinitionException.class)
    public ProblemDetail handleInvalid(InvalidStrategyDefinitionException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Invalid strategy definition");
        problem.setProperty("field", e.path());
        return problem;
    }

    /** Syntactically valid CSV row violating market-data semantics (D-32 §5/§6) → 422. */
    @ExceptionHandler(InvalidCsvDataException.class)
    public ProblemDetail handleInvalidCsvData(InvalidCsvDataException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Invalid dataset data");
        problem.setProperty("line", e.line());
        return problem;
    }

    /** Missing resource, or another owner's resource (D-31 §10/D-32 §15: identical either way) → 404. */
    @ExceptionHandler({StrategyNotFoundException.class, StrategyVersionNotFoundException.class,
            DatasetNotFoundException.class, DatasetVersionNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Not found");
        return problem;
    }

    /** Duplicate name, or a version-allocation conflict (strategy or dataset) → 409. */
    @ExceptionHandler({DuplicateStrategyNameException.class, StrategyVersionConflictException.class,
            DuplicateDatasetNameException.class, DatasetVersionConflictException.class})
    public ProblemDetail handleConflict(RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }

    /**
     * Stored strategy-definition integrity failure — corrupted or tampered
     * data, or a codec/engine disagreement. Never client-facing detail: the
     * cause is logged, and the response carries only a generic message
     * (D-30's own contract for {@link StrategyDefinitionIntegrityException}).
     */
    @ExceptionHandler(StrategyDefinitionIntegrityException.class)
    public ProblemDetail handleIntegrityFailure(StrategyDefinitionIntegrityException e) {
        log.error("stored strategy-definition integrity failure", e);
        return genericServerError();
    }

    /**
     * Stored dataset-version integrity failure — corrupted or tampered
     * bar data, or a codec/engine disagreement (D-32 §16). Never
     * client-facing detail: the cause is logged, and the response carries
     * only a generic message (mirroring {@link StrategyDefinitionIntegrityException}'s
     * own contract).
     */
    @ExceptionHandler(DatasetIntegrityException.class)
    public ProblemDetail handleDatasetIntegrityFailure(DatasetIntegrityException e) {
        log.error("stored dataset integrity failure", e);
        return genericServerError();
    }

    /** Catch-all: any other unexpected failure → 500, logged, never detailed to the client. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("unexpected error handling request", e);
        return genericServerError();
    }

    /**
     * Upload larger than the configured multipart limit (D-32 §3) → 413.
     * {@link ResponseEntityExceptionHandler} already maps this exception
     * internally, so it is overridden here — rather than given its own
     * {@code @ExceptionHandler} method, which would conflict with the
     * inherited one — purely to guarantee the same fixed {@link
     * ProblemDetail} shape as every other handler in this class.
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "uploaded file is too large");
        problem.setTitle("Payload too large");
        return handleExceptionInternal(e, problem, headers, HttpStatusCode.valueOf(413), request);
    }

    private static ProblemDetail genericServerError() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "an internal error occurred");
        problem.setTitle("Internal error");
        return problem;
    }
}
