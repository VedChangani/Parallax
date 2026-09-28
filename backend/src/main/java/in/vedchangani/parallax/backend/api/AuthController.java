package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionCodec;
import in.vedchangani.parallax.backend.user.CurrentUser;
import in.vedchangani.parallax.backend.user.ParallaxUserPrincipal;
import in.vedchangani.parallax.backend.user.PasswordChangeService;
import in.vedchangani.parallax.backend.user.UserRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * The D-37 authentication status endpoint, plus D-38 self-registration and
 * D-40 password change. Login ({@code POST /api/auth/login}) and logout
 * ({@code POST /api/auth/logout}) are handled entirely by Spring
 * Security's {@code formLogin}/{@code logout} filters (see {@code
 * SecurityConfig}) — this controller only answers "who is the current
 * session, if anyone", creates new accounts, and changes an existing
 * password, none of which is itself a Spring Security concern.
 *
 * <p>{@code GET /api/auth/me} is deliberately not routed through {@link
 * in.vedchangani.parallax.backend.user.CurrentUser}: that seam always
 * resolves an owner id for an already-authenticated request and throws
 * otherwise (D-37), whereas this endpoint is explicitly reachable while
 * anonymous — it is also the request the frontend uses to bootstrap the
 * CSRF cookie on load.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRegistrationService userRegistrationService;
    private final PasswordChangeService passwordChangeService;
    private final StrategyDefinitionCodec codec;
    private final CurrentUser currentUser;
    private final Validator validator;

    public AuthController(UserRegistrationService userRegistrationService, PasswordChangeService passwordChangeService,
                           StrategyDefinitionCodec codec, CurrentUser currentUser, Validator validator) {
        this.userRegistrationService = userRegistrationService;
        this.passwordChangeService = passwordChangeService;
        this.codec = codec;
        this.currentUser = currentUser;
        this.validator = validator;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal ParallaxUserPrincipal principal) {
        if (principal == null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                    "authentication is required");
            problem.setTitle("Unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }
        return ResponseEntity.ok(Map.of("username", principal.getUsername()));
    }

    /**
     * D-38: creates a new, passwordless-no-more account. Never logs the
     * caller in — a separate {@code POST /api/auth/login} call is required
     * afterward — and never changes the identity of an already-
     * authenticated caller who happens to call this endpoint (D-38: the
     * request body carries no session-affecting information at all).
     *
     * <p>The body is read exactly once by the D-30 strict codec (never
     * Spring's global JSON binding), which is what rejects an unknown
     * property such as an attempted {@code id}, {@code passwordHash}, or
     * {@code ownerId} before Bean Validation ever runs.
     */
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> register(@RequestBody String body) {
        RegisterRequest request = codec.parseRequest(body, RegisterRequest.class);
        validate(request);

        userRegistrationService.register(request.username(), request.password());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", request.username()));
    }

    /**
     * D-40: changes the authenticated caller's own password — {@code
     * CurrentUser} is the sole source of which account, exactly like every
     * other owner-scoped endpoint in this backend, so this can never touch
     * another user's password. {@code 204} with no body on success, never
     * echoing either password or the stored hash. The session id is
     * rotated afterward (the same fixation-protection Spring Security's
     * own login flow performs) — since the session already carries an
     * established {@code SecurityContext}, {@link
     * HttpServletRequest#changeSessionId()} alone keeps the caller
     * authenticated under the new id, no re-login required.
     *
     * <p>A wrong {@code currentPassword} → {@code
     * InvalidCurrentPasswordException} (401, the same generic body a
     * failed login gets); a {@code newPassword} failing {@link
     * in.vedchangani.parallax.backend.user.PasswordPolicy} → {@code
     * WeakPasswordException} (400) — both mapped by {@code
     * ApiExceptionHandler}, and both leave the stored hash untouched.
     */
    @PostMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> changePassword(@RequestBody String body, HttpServletRequest request) {
        ChangePasswordRequest changeRequest = codec.parseRequest(body, ChangePasswordRequest.class);

        passwordChangeService.changePassword(currentUser.id(), changeRequest.currentPassword(),
                changeRequest.newPassword());
        request.changeSessionId();

        return ResponseEntity.noContent().build();
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
