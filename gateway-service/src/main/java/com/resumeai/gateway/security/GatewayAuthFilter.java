package com.resumeai.gateway.security;

import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import java.util.regex.Pattern;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final Pattern NOTIFICATIONS_STREAM_PATTERN = Pattern.compile("/api/v1/notifications/stream/\\d+");
    private static final Pattern RESUMES_PATTERN = Pattern.compile("/api/v1/resumes/\\d+");
    private static final Pattern RESUMES_VIEWS_PATTERN = Pattern.compile("/api/v1/resumes/\\d+/views/increment");
    
    private static final String TEMPLATES_API = "/api/v1/templates";
    private static final List<String> PUBLIC_PATTERNS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/oauth2/providers",
            "/api/v1/auth/profile-picture/**",
            "/oauth2/**",
            "/login/oauth2/**",
            "/api/v1/web/home",
            "/api/v1/web/templates",
            "/api/v1/web/gallery",
            "/fallback/**",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**");
    private static final List<String> ADMIN_PATTERNS = List.of(
            "/api/v1/auth/users",
            "/api/v1/auth/users/**",
            "/api/v1/auth/audit-logs",
            TEMPLATES_API,
            TEMPLATES_API + "/**",
            "/api/v1/web/admin/**",
            "/api/v1/payments/admin/**",
            "/api/v1/payments/admin",
            "/api/v1/notifications/bulk");
    private static final List<String> PREMIUM_PATTERNS = List.of(
            "/api/v1/ai/generate-cover-letter",
            "/api/v1/ai/improve-section",
            "/api/v1/ai/tailor-for-job",
            "/api/v1/ai/translate",
            "/api/v1/exports/docx",
            "/api/v1/exports/json",
            "/api/v1/job-matches/**");

    private final GatewayJwtUtil jwtUtil;

    public GatewayAuthFilter(GatewayJwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        String query = exchange.getRequest().getURI().getRawQuery();
        if (isPublic(method, path, query)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange.getRequest());
        Claims claims;
        try {
            if (!StringUtils.hasText(token)) {
                return writeError(exchange.getResponse(), HttpStatus.UNAUTHORIZED,
                        "Authentication is required for this route.");
            }
            claims = jwtUtil.parse(token);
        } catch (Exception ex) {
            return writeError(exchange.getResponse(), HttpStatus.UNAUTHORIZED,
                    "Authentication token is invalid or expired.");
        }

        String role = String.valueOf(claims.getOrDefault("role", "USER"));
        String plan = String.valueOf(claims.getOrDefault("plan", "FREE"));
        if (NOTIFICATIONS_STREAM_PATTERN.matcher(path).matches()
                && !"ADMIN".equalsIgnoreCase(role)
                && !path.endsWith("/" + claims.getSubject())) {
            return writeError(exchange.getResponse(), HttpStatus.FORBIDDEN,
                    "Users can only stream their own notifications.");
        }
        if (matchesAny(path, ADMIN_PATTERNS) && !"ADMIN".equalsIgnoreCase(role)) {
            return writeError(exchange.getResponse(), HttpStatus.FORBIDDEN,
                    "Admin access is required for this route.");
        }
        if (matchesAny(path, PREMIUM_PATTERNS)
                && !"PREMIUM".equalsIgnoreCase(plan)
                && !"ADMIN".equalsIgnoreCase(role)) {
            return writeError(exchange.getResponse(), HttpStatus.FORBIDDEN,
                    "Premium access is required for this route.");
        }
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Email");
                    headers.remove("X-User-Role");
                    headers.remove("X-User-Plan");
                    headers.set("X-User-Id", String.valueOf(claims.getSubject()));
                    headers.set("X-User-Email", String.valueOf(claims.getOrDefault("email", "")));
                    headers.set("X-User-Role", role);
                    headers.set("X-User-Plan", plan);
                })
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isPublic(String method, String path, String query) {
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        if (isReadMethod(method) && "/api/v1/resumes".equals(path) && hasTrueQueryParam(query, "publicOnly")) {
            return true;
        }
        if (isReadMethod(method) && RESUMES_PATTERN.matcher(path).matches() && hasTrueQueryParam(query, "public")) {
            return true;
        }
        if ("PUT".equalsIgnoreCase(method)
                && RESUMES_VIEWS_PATTERN.matcher(path).matches()
                && hasTrueQueryParam(query, "public")) {
            return true;
        }
        if (isReadMethod(method) && path.startsWith(TEMPLATES_API)) {
            return true;
        }
        return matchesAny(path, PUBLIC_PATTERNS);
    }

    private boolean isReadMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private boolean hasTrueQueryParam(String query, String name) {
        if (!StringUtils.hasText(query)) {
            return false;
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && name.equals(pair[0]) && "true".equalsIgnoreCase(pair[1])) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAny(String path, List<String> patterns) {
        return patterns.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (NOTIFICATIONS_STREAM_PATTERN.matcher(request.getURI().getPath()).matches()) {
            String queryToken = request.getQueryParams().getFirst("access_token");
            if (StringUtils.hasText(queryToken)) {
                return queryToken;
            }
        }
        return null;
    }

    private Mono<Void> writeError(ServerHttpResponse response, HttpStatus status, String message) {
        response.setStatusCode(status);
        response.getHeaders().set("Content-Type", "application/json");
        byte[] body = ("{\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
