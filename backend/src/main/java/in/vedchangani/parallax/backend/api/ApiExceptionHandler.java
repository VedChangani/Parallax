package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.backtest.BacktestRangeException;
import in.vedchangani.parallax.backend.backtest.BacktestResultIntegrityException;
import in.vedchangani.parallax.backend.backtest.BacktestRunNotFoundException;
import in.vedchangani.parallax.backend.backtest.InvalidBacktestConfigException;
import in.vedchangani.parallax.backend.backtest.MalformedBacktestConfigException;
import in.vedchangani.parallax.backend.dataset.DatasetIntegrityException;
import in.vedchangani.parallax.backend.dataset.DatasetNotFoundException;
import in.vedchangani.parallax.backend.dataset.DatasetVersionConflictException;
import in.vedchangani.parallax.backend.dataset.DatasetVersionNotFoundException;
import in.vedchangani.parallax.backend.dataset.DuplicateDatasetNameException;
import in.vedchangani.parallax.backend.dataset.csv.InvalidCsvDataException;
import in.vedchangani.parallax.backend.dataset.csv.MalformedCsvException;
import in.vedchangani.parallax.backend.marketdata.InvalidMarketDataException;
import in.vedchangani.parallax.backend.marketdata.MarketDataCapabilityException;
import in.vedchangani.parallax.backend.marketdata.MarketDataRequestRejectedException;
import in.vedchangani.parallax.backend.marketdata.MarketDataResponseException;
import in.vedchangani.parallax.backend.marketdata.MarketDataUnavailableException;
import in.vedchangani.parallax.backend.strategy.DuplicateStrategyNameException;
import in.vedchangani.parallax.backend.strategy.StrategyNotFoundException;
import in.vedchangani.parallax.backend.strategy.StrategyVersionConflictException;
import in.vedchangani.parallax.backend.strategy.StrategyVersionNotFoundException;
import in.vedchangani.parallax.backend.strategy.definition.InvalidStrategyDefinitionException;
import in.vedchangani.parallax.backend.strategy.definition.MalformedStrategyDefinitionException;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionIntegrityException;
import in.vedchangani.parallax.backend.user.DuplicateUsernameException;
import in.vedchangani.parallax.backend.user.InvalidCurrentPasswordException;
import in.vedchangani.parallax.backend.user.RegistrationDisabledException;
import in.vedchangani.parallax.backend.user.WeakPasswordException;
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

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MalformedStrategyDefinitionException.class)
    public ProblemDetail handleMalformed(MalformedStrategyDefinitionException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Malformed request");
        problem.setProperty("field", e.path());
        return problem;
    }

    @ExceptionHandler(MalformedBacktestConfigException.class)
    public ProblemDetail handleMalformedBacktestConfig(MalformedBacktestConfigException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Malformed request");
        problem.setProperty("field", e.path());
        return problem;
    }

    @ExceptionHandler(MalformedCsvException.class)
    public ProblemDetail handleMalformedCsv(MalformedCsvException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Malformed request");
        problem.setProperty("line", e.line());
        return problem;
    }

    @ExceptionHandler(MalformedDatasetUploadException.class)
    public ProblemDetail handleMalformedDatasetUpload(MalformedDatasetUploadException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Malformed request");
        problem.setProperty("field", e.field());
        return problem;
    }

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

    @ExceptionHandler(WeakPasswordException.class)
    public ProblemDetail handleWeakPassword(WeakPasswordException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Malformed request");
        problem.setProperty("field", e.field());
        return problem;
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ProblemDetail handleInvalidCurrentPassword(InvalidCurrentPasswordException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problem.setTitle("Unauthorized");
        return problem;
    }

    @ExceptionHandler(InvalidStrategyDefinitionException.class)
    public ProblemDetail handleInvalid(InvalidStrategyDefinitionException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Invalid strategy definition");
        problem.setProperty("field", e.path());
        return problem;
    }

    @ExceptionHandler(InvalidBacktestConfigException.class)
    public ProblemDetail handleInvalidBacktestConfig(InvalidBacktestConfigException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Invalid backtest configuration");
        problem.setProperty("field", e.path());
        return problem;
    }

    @ExceptionHandler(BacktestRangeException.class)
    public ProblemDetail handleBacktestRange(BacktestRangeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Invalid backtest range");
        problem.setProperty("requestedStartDate", e.requestedStartDate());
        problem.setProperty("requestedEndDate", e.requestedEndDate());
        problem.setProperty("datasetFirstDate", e.datasetFirstDate());
        problem.setProperty("datasetLastDate", e.datasetLastDate());
        return problem;
    }

    @ExceptionHandler(InvalidCsvDataException.class)
    public ProblemDetail handleInvalidCsvData(InvalidCsvDataException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Invalid dataset data");
        problem.setProperty("line", e.line());
        return problem;
    }

    @ExceptionHandler(MarketDataRequestRejectedException.class)
    public ProblemDetail handleMarketDataRequestRejected(MarketDataRequestRejectedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Market data request rejected");
        return problem;
    }

    @ExceptionHandler(MarketDataCapabilityException.class)
    public ProblemDetail handleMarketDataCapability(MarketDataCapabilityException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Market data capability limit");
        return problem;
    }

    @ExceptionHandler(InvalidMarketDataException.class)
    public ProblemDetail handleInvalidMarketData(InvalidMarketDataException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Invalid market data");
        e.date().ifPresent(date -> problem.setProperty("date", date));
        return problem;
    }

    @ExceptionHandler(MarketDataUnavailableException.class)
    public ProblemDetail handleMarketDataUnavailable(MarketDataUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setTitle("Market data unavailable");
        return problem;
    }

    @ExceptionHandler(MarketDataResponseException.class)
    public ProblemDetail handleMarketDataResponse(MarketDataResponseException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
        problem.setTitle("Market data provider error");
        return problem;
    }

    @ExceptionHandler({StrategyNotFoundException.class, StrategyVersionNotFoundException.class,
            DatasetNotFoundException.class, DatasetVersionNotFoundException.class,
            BacktestRunNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Not found");
        return problem;
    }

    @ExceptionHandler({DuplicateStrategyNameException.class, StrategyVersionConflictException.class,
            DuplicateDatasetNameException.class, DatasetVersionConflictException.class,
            DuplicateUsernameException.class})
    public ProblemDetail handleConflict(RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler(RegistrationDisabledException.class)
    public ProblemDetail handleRegistrationDisabled(RegistrationDisabledException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problem.setTitle("Forbidden");
        return problem;
    }

    @ExceptionHandler(StrategyDefinitionIntegrityException.class)
    public ProblemDetail handleIntegrityFailure(StrategyDefinitionIntegrityException e) {
        log.error("stored strategy-definition integrity failure", e);
        return genericServerError();
    }

    @ExceptionHandler(DatasetIntegrityException.class)
    public ProblemDetail handleDatasetIntegrityFailure(DatasetIntegrityException e) {
        log.error("stored dataset integrity failure", e);
        return genericServerError();
    }

    @ExceptionHandler(BacktestResultIntegrityException.class)
    public ProblemDetail handleBacktestResultIntegrityFailure(BacktestResultIntegrityException e) {
        log.error("stored backtest run failed integrity verification", e);
        return genericServerError();
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("unexpected error handling request", e);
        return genericServerError();
    }

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
