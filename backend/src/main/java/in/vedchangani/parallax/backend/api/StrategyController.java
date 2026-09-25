package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.strategy.StrategyService;
import in.vedchangani.parallax.backend.strategy.StrategySummary;
import in.vedchangani.parallax.backend.strategy.StrategyVersionDetail;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionCodec;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionDto;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionMapper;
import in.vedchangani.parallax.backend.user.CurrentUser;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * The D-31 Strategy REST API. Every strategy-definition-bearing request body
 * is read exactly once, by the D-30 {@link StrategyDefinitionCodec}'s own
 * strict {@code JsonMapper} — never Spring's global JSON binding (D-31 §8).
 * Handlers therefore declare {@code @RequestBody String body} rather than a
 * typed DTO parameter, and parse it themselves via {@link
 * StrategyDefinitionCodec#parseRequest(String, Class)}. Bean Validation on
 * the plain envelope fields ({@code name}/{@code description}) is invoked
 * explicitly, since {@code @Valid} cannot apply to a raw {@code String}
 * parameter.
 *
 * <p>The owner is always {@link CurrentUser#id()} — never accepted from a
 * request. No DELETE or PUT endpoint exists (D-31 §7).
 */
@RestController
@RequestMapping("/api/strategies")
public class StrategyController {

    private final StrategyService strategyService;
    private final StrategyDefinitionCodec codec;
    private final StrategyDefinitionMapper mapper;
    private final CurrentUser currentUser;
    private final Validator validator;

    public StrategyController(StrategyService strategyService, StrategyDefinitionCodec codec,
                               StrategyDefinitionMapper mapper, CurrentUser currentUser, Validator validator) {
        this.strategyService = strategyService;
        this.codec = codec;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.validator = validator;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StrategyResponse> createStrategy(@RequestBody String body) {
        CreateStrategyRequest request = codec.parseRequest(body, CreateStrategyRequest.class);
        validate(request);
        StrategyDefinition definition = mapper.toEngine(request.definition(), "definition");

        StrategySummary summary = strategyService.createStrategy(currentUser.id(), request.name(),
                request.description(), definition);

        return ResponseEntity.created(URI.create("/api/strategies/" + summary.id()))
                .body(StrategyResponse.of(summary));
    }

    @GetMapping
    public List<StrategyResponse> listStrategies() {
        return strategyService.listStrategies(currentUser.id()).stream()
                .map(StrategyResponse::of)
                .toList();
    }

    @GetMapping("/{id}")
    public StrategyResponse getStrategy(@PathVariable long id) {
        return StrategyResponse.of(strategyService.getStrategy(currentUser.id(), id));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public StrategyResponse updateStrategyMetadata(@PathVariable long id, @RequestBody String body) {
        UpdateStrategyMetadataRequest request = codec.parseRequest(body, UpdateStrategyMetadataRequest.class);
        validate(request);

        StrategySummary summary = strategyService.updateStrategyMetadata(currentUser.id(), id, request.name(),
                request.description());

        return StrategyResponse.of(summary);
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StrategyVersionResponse> createVersion(@PathVariable long id, @RequestBody String body) {
        StrategyDefinitionDto dto = codec.parseRequest(body);
        StrategyDefinition definition = mapper.toEngine(dto);

        StrategyVersionDetail detail = strategyService.createVersion(currentUser.id(), id, definition);

        URI location = URI.create("/api/strategies/" + id + "/versions/" + detail.summary().versionNumber());
        return ResponseEntity.created(location).body(StrategyVersionResponse.of(detail, mapper));
    }

    @GetMapping("/{id}/versions")
    public List<StrategyVersionSummaryResponse> listVersions(@PathVariable long id) {
        return strategyService.listVersions(currentUser.id(), id).stream()
                .map(StrategyVersionSummaryResponse::of)
                .toList();
    }

    @GetMapping("/{id}/versions/{version}")
    public StrategyVersionResponse getVersion(@PathVariable long id, @PathVariable int version) {
        StrategyVersionDetail detail = strategyService.getVersion(currentUser.id(), id, version);
        return StrategyVersionResponse.of(detail, mapper);
    }

    /**
     * Explicit Bean Validation of an envelope record already produced by the
     * strict D-30 reader. {@code @Valid} cannot be applied to a raw {@code
     * String} request-body parameter, so this substitutes for it.
     */
    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
