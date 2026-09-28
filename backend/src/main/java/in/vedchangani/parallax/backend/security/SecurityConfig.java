package in.vedchangani.parallax.backend.security;

import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * The D-37 security filter chain: server-side session authentication for
 * every {@code /api/**} endpoint, cookie-based SPA CSRF protection, and no
 * browser-facing login page (this backend is a pure JSON API — the SPA
 * owns the login form). See docs/decisions.md D-37 for the full rationale
 * and the exact invariants this configuration must uphold.
 *
 * <p>{@code HttpSecurity} pre-applies a baseline of configurers with
 * secure defaults before this method runs — {@code
 * SessionManagementConfigurer} (session-per-login, {@code
 * changeSessionId()} fixation protection), {@code HeadersConfigurer}
 * ({@code Cache-Control: no-store}, {@code X-Content-Type-Options},
 * {@code X-Frame-Options}, HSTS), and {@code SecurityContextConfigurer}
 * ({@code HttpSessionSecurityContextRepository}) among them — so only
 * deviations from those defaults are configured explicitly below. {@code
 * httpBasic}, {@code rememberMe}, and {@code oauth2Login} are opt-in and
 * are simply never enabled, rather than explicitly disabled.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Every new hash is stored as {@code {bcrypt}$2a$...} — the {@code
     * {id}} prefix leaves room for a future algorithm upgrade with no data
     * migration (D-37).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll()
                )
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        // Setting a custom login page URL marks
                        // isCustomLoginPage() true, so Spring Security never
                        // populates DefaultLoginPageGeneratingFilter with
                        // this path - there is no generated HTML login page
                        // anywhere (AuthenticationIT asserts this for GET
                        // /login, the framework's own unconfigured default).
                        .loginPage("/api/auth/login")
                        .successHandler(new JsonAuthenticationSuccessHandler(objectMapper))
                        .failureHandler(new GenericAuthenticationFailureHandler(objectMapper))
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("JSESSIONID")
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new ProblemDetailAccessDeniedHandler(objectMapper))
                )
                // The SPA-oriented cookie CSRF repository/request handler
                // (Spring Security 7's csrf().spa()): a non-HttpOnly
                // XSRF-TOKEN cookie plus X-XSRF-TOKEN header, with BREACH
                // protection via XOR encoding. Its cookie's Secure
                // attribute is left to CookieCsrfTokenRepository's own
                // default (request.isSecure()) rather than tied to a
                // config property: spa() does not expose the repository
                // instance afterward for further customization, and
                // mirroring the actual request scheme is at least as
                // correct as a static flag.
                .csrf(csrf -> csrf.spa())
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // Active by baseline default; disabled because a 401 for an
                // anonymous request must never create a session merely to
                // remember it for a post-login redirect this API never
                // performs.
                .requestCache(cache -> cache.disable());

        return http.build();
    }
}
