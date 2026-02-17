package com.alves_dev.sos.security;

import com.alves_dev.sos.config.ServerConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class ApiKeyValidator {

    private final ServerConfig serverConfig;

    public ApiKeyValidator(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    public boolean isValid(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return false;
        }
        List<String> validKeys = serverConfig.getApiKeys();
        return validKeys != null && validKeys.contains(apiKey);
    }
}
