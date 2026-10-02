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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
                .csrf(csrf -> csrf.spa())
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .requestCache(cache -> cache.disable());

        return http.build();
    }
}
