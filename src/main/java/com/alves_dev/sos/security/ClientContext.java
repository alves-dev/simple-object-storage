package com.alves_dev.sos.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class ClientContext {

    private ClientContext() {
    }

    public static AuthenticatedClient requireCurrentClient() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedClient client)) {
            throw new IllegalStateException("No authenticated client is available");
        }
        return client;
    }
}
