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

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> register(@RequestBody String body) {
        RegisterRequest request = codec.parseRequest(body, RegisterRequest.class);
        validate(request);

        userRegistrationService.register(request.username(), request.password());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", request.username()));
    }

    @PostMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> changePassword(@RequestBody String body, HttpServletRequest request) {
        ChangePasswordRequest changeRequest = codec.parseRequest(body, ChangePasswordRequest.class);

        passwordChangeService.changePassword(currentUser.id(), changeRequest.currentPassword(),
                changeRequest.newPassword());
        request.changeSessionId();

        return ResponseEntity.noContent().build();
    }

    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
