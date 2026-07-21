package com.alves_dev.sos.security;

public record AuthenticatedClient(String clientId, boolean admin) {
}
