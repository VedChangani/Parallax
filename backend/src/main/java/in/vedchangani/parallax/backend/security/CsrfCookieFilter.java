package in.vedchangani.parallax.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces the deferred {@link CsrfToken} to resolve on every request
 * (D-37), which is what actually makes {@code CookieCsrfTokenRepository}
 * write the {@code XSRF-TOKEN} cookie. Without this filter, Spring
 * Security only resolves the token lazily when something reads the
 * {@code _csrf} request attribute — normally a server-rendered view — and
 * nothing ever does that in a pure JSON API, so the cookie would never be
 * set, including on the anonymous {@code GET /api/auth/me} the frontend
 * calls on load to bootstrap it. This is the standard filter from Spring
 * Security's own SPA CSRF guide, added once, directly after {@code
 * CsrfFilter}, with no path restriction.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        if (csrfToken != null) {
            // Merely reading the token resolves the deferred supplier,
            // which is what triggers CookieCsrfTokenRepository to write
            // the cookie on this response.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
