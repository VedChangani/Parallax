package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.dataset.AdjustmentBasis;
import in.vedchangani.parallax.backend.dataset.DatasetService;
import in.vedchangani.parallax.backend.dataset.DatasetSummary;
import in.vedchangani.parallax.backend.dataset.DatasetVersionSummary;
import in.vedchangani.parallax.backend.dataset.VerifiedDatasetVersion;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionCodec;
import in.vedchangani.parallax.backend.user.CurrentUser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The D-32 Dataset REST API. Every JSON request body is read exactly once
 * by the D-30 {@link StrategyDefinitionCodec}'s own strict {@code
 * JsonMapper} — never Spring's global JSON binding (mirroring D-31 §8) —
 * and version creation is read directly off a {@link
 * MultipartHttpServletRequest} rather than Spring's {@code @RequestParam}
 * binding, so the exact-shape multipart contract (exactly one {@code file}
 * part, exactly one {@code adjustmentBasis} field, nothing else) can be
 * enforced explicitly instead of silently ignoring extra parts.
 *
 * <p>The owner is always {@link CurrentUser#id()} — never accepted from a
 * request. No PATCH/PUT/DELETE endpoint exists (D-32 has no dataset
 * metadata update in this batch).
 */
@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private static final Set<String> ALLOWED_FILE_FIELDS = Set.of("file");
    private static final Set<String> ALLOWED_PARAMETER_FIELDS = Set.of("adjustmentBasis");
    private static final int MAX_FILENAME_LENGTH = 255;

    private final DatasetService datasetService;
    private final StrategyDefinitionCodec codec;
    private final CurrentUser currentUser;
    private final Validator validator;

    public DatasetController(DatasetService datasetService, StrategyDefinitionCodec codec, CurrentUser currentUser,
                              Validator validator) {
        this.datasetService = datasetService;
        this.codec = codec;
        this.currentUser = currentUser;
        this.validator = validator;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DatasetResponse> createDataset(@RequestBody String body) {
        CreateDatasetRequest request = codec.parseRequest(body, CreateDatasetRequest.class);
        validate(request);

        DatasetSummary summary = datasetService.createDataset(currentUser.id(), request.name(), request.symbol());

        return ResponseEntity.created(URI.create("/api/datasets/" + summary.id()))
                .body(DatasetResponse.of(summary));
    }

    @GetMapping
    public List<DatasetResponse> listDatasets() {
        return datasetService.listDatasets(currentUser.id()).stream()
                .map(DatasetResponse::of)
                .toList();
    }

    @GetMapping("/{id}")
    public DatasetResponse getDataset(@PathVariable long id) {
        return DatasetResponse.of(datasetService.getDataset(currentUser.id(), id));
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DatasetVersionResponse> createVersion(@PathVariable long id,
                                                                  MultipartHttpServletRequest request) {
        MultipartFile filePart = requireExactlyOneFile(request);
        AdjustmentBasis basis = requireExactlyOneAdjustmentBasis(request);
        String filename = requireValidFilename(filePart.getOriginalFilename());
        byte[] csv = readBytes(filePart);

        DatasetVersionSummary summary =
                datasetService.createVersionFromCsv(currentUser.id(), id, csv, basis, filename);

        URI location = URI.create("/api/datasets/" + id + "/versions/" + summary.versionNumber());
        return ResponseEntity.created(location).body(DatasetVersionResponse.of(summary));
    }

    /**
     * D-33 Batch 4: creates a new {@code DatasetVersion} from Alpha
     * Vantage's daily bars, using the exact same strict-JSON envelope
     * pattern as {@link #createDataset}. This method only parses/validates
     * the request and delegates to {@link
     * DatasetService#createVersionFromAlphaVantage} — it never calls a
     * {@code MarketDataProvider} itself, performs no ownership check of its
     * own (the service is authoritative, exactly like every other
     * endpoint here), and contains no persistence or canonicalization/hash
     * logic. The response reuses {@link DatasetVersionResponse} unchanged —
     * an Alpha Vantage-created version is not a different resource shape
     * than a CSV-uploaded one.
     */
    @PostMapping(value = "/{id}/versions/alpha-vantage", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DatasetVersionResponse> createVersionFromAlphaVantage(@PathVariable long id,
                                                                                 @RequestBody String body) {
        AlphaVantageImportRequest request = codec.parseRequest(body, AlphaVantageImportRequest.class);
        validate(request);

        DatasetVersionSummary summary =
                datasetService.createVersionFromAlphaVantage(currentUser.id(), id, request.historyDepth());

        URI location = URI.create("/api/datasets/" + id + "/versions/" + summary.versionNumber());
        return ResponseEntity.created(location).body(DatasetVersionResponse.of(summary));
    }

    @GetMapping("/{id}/versions")
    public List<DatasetVersionResponse> listVersions(@PathVariable long id) {
        return datasetService.listVersions(currentUser.id(), id).stream()
                .map(DatasetVersionResponse::of)
                .toList();
    }

    @GetMapping("/{id}/versions/{version}")
    public DatasetVersionResponse getVersion(@PathVariable long id, @PathVariable int version) {
        return DatasetVersionResponse.of(datasetService.getVersion(currentUser.id(), id, version));
    }

    @GetMapping("/{id}/versions/{version}/bars")
    public DatasetBarsResponse getBars(@PathVariable long id, @PathVariable int version) {
        VerifiedDatasetVersion verified = datasetService.getVerifiedSeries(currentUser.id(), id, version);
        return DatasetBarsResponse.of(verified);
    }

    // --- multipart contract enforcement --------------------------------------

    private static MultipartFile requireExactlyOneFile(MultipartHttpServletRequest request) {
        Map<String, List<MultipartFile>> fileMap = request.getMultiFileMap();
        if (!fileMap.keySet().equals(ALLOWED_FILE_FIELDS) || fileMap.get("file").size() != 1) {
            throw new MalformedDatasetUploadException("file", "expected exactly one file part named \"file\"");
        }
        return fileMap.get("file").get(0);
    }

    private static AdjustmentBasis requireExactlyOneAdjustmentBasis(MultipartHttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (!parameterMap.keySet().equals(ALLOWED_PARAMETER_FIELDS) || parameterMap.get("adjustmentBasis").length != 1) {
            throw new MalformedDatasetUploadException("adjustmentBasis",
                    "expected exactly one \"adjustmentBasis\" field");
        }
        String text = parameterMap.get("adjustmentBasis")[0];
        try {
            return AdjustmentBasis.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new MalformedDatasetUploadException("adjustmentBasis", "invalid adjustmentBasis: " + text);
        }
    }

    private static String requireValidFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new MalformedDatasetUploadException("file", "uploaded file must have a filename");
        }
        if (filename.length() > MAX_FILENAME_LENGTH) {
            throw new MalformedDatasetUploadException("file",
                    "filename must be at most " + MAX_FILENAME_LENGTH + " characters");
        }
        for (int i = 0; i < filename.length(); i++) {
            if (Character.isISOControl(filename.charAt(i))) {
                throw new MalformedDatasetUploadException("file", "filename must not contain control characters");
            }
        }
        return filename;
    }

    private static byte[] readBytes(MultipartFile filePart) {
        try {
            return filePart.getBytes();
        } catch (IOException e) {
            throw new MalformedDatasetUploadException("file", "could not read uploaded file");
        }
    }

    /**
     * Explicit Bean Validation of an envelope record already produced by
     * the strict D-30 reader (mirroring {@code StrategyController}).
     * {@code @Valid} cannot be applied to a raw {@code String} request-body
     * parameter, so this substitutes for it.
     */
    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
