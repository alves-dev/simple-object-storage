package com.alves_dev.sos.security;

import com.alves_dev.sos.model.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyValidator apiKeyValidator;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(ApiKeyValidator apiKeyValidator, ObjectMapper objectMapper) {
        this.apiKeyValidator = apiKeyValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();

        // Only filter upload and delete endpoints
        boolean isUpload = HttpMethod.POST.matches(method) && path.equals("/api/files/upload");
        boolean isDelete = HttpMethod.DELETE.matches(method) && path.startsWith("/api/files/");

        return !isUpload && !isDelete;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (!apiKeyValidator.isValid(apiKey)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiResponse<?> errorResponse = ApiResponse.error("UNAUTHORIZED", "Valid API Key is required");
            objectMapper.writeValue(response.getWriter(), errorResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }
}