package com.alves_dev.sos.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class FileApiControllerTests {

    private final FileApiController controller = new FileApiController();

    @Test
    void legacyJsonEndpointsReturnGone() {
        assertThat(controller.uploadGone().getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(controller.infoGone("id").getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(controller.deleteGone("id").getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(controller.bucketGone("bucket").getStatusCode()).isEqualTo(HttpStatus.GONE);
    }
}
