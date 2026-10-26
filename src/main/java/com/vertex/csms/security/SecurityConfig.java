package com.vertex.csms.security;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vertex.csms.common.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Session-cookie login with two roles (REQ-ACC-1/2). Role checks happen on the server for every
 * API call (REQ-SEC-2): URL rules below plus @PreAuthorize on Owner-only endpoints.
 *
 * CSRF tokens are switched off because the API only accepts JSON and the session cookie is
 * SameSite=Strict, so another website cannot make the browser send it.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper mapper;

    public SecurityConfig(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(c -> c.disable())
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            .securityContext(s -> s.securityContextRepository(securityContextRepository()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/v1/auth/login", "/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/reports/**", "/api/v1/purchases/**", "/api/v1/suppliers/**",
                        "/api/v1/users/**", "/api/v1/alerts/**").hasRole("OWNER")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())   // static HTML/CSS/JS; each page checks /auth/me itself
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                        write(res, 401, "ERR-ACC-004", "Session expired or not logged in. Please log in again."))
                .accessDeniedHandler((req, res, ex) ->
                        write(res, 403, "ERR-ACC-003", "This action is not permitted for your role.")));
        return http.build();
    }

    private void write(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ErrorResponse.of(code, message, null));
    }
}
