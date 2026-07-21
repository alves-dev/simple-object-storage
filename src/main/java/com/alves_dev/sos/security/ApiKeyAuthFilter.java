package com.alves_dev.sos.security;

import com.alves_dev.sos.model.dto.ApiResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ClientAuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(ClientAuthenticationService authenticationService, ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api/v2/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        try {
            AuthenticatedClient client = authenticationService.authenticate(apiKey);
            var authentication = UsernamePasswordAuthenticationToken.authenticated(client, null, java.util.List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (com.alves_dev.sos.exception.ClientDisabledException exception) {
            writeError(response, HttpStatus.FORBIDDEN, "CLIENT_DISABLED", exception.getMessage());
        } catch (com.alves_dev.sos.exception.InvalidApiKeyException exception) {
            String code = apiKey == null || apiKey.isBlank() ? "UNAUTHORIZED" : "INVALID_API_KEY";
            writeError(response, HttpStatus.UNAUTHORIZED, code, exception.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponseDto.error(code, message));
    }
}
