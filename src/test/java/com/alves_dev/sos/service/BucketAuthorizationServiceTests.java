package com.alves_dev.sos.service;

import com.alves_dev.sos.exception.AccessDeniedException;
import com.alves_dev.sos.model.Bucket;
import com.alves_dev.sos.model.BucketAction;
import com.alves_dev.sos.model.BucketPermission;
import com.alves_dev.sos.repository.BucketPermissionRepository;
import com.alves_dev.sos.security.AuthenticatedClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BucketAuthorizationServiceTests {

    private final BucketPermissionRepository repository = mock(BucketPermissionRepository.class);
    private final BucketAuthorizationService service = new BucketAuthorizationService(repository);
    private final Bucket bucket = new Bucket("Photos", "owner");

    @Test
    void adminBypassesPermissions() {
        assertThatCode(() -> service.requireUpload(new AuthenticatedClient("admin", true), bucket))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.requireOwnerOrAdmin(new AuthenticatedClient("admin", true), bucket))
                .doesNotThrowAnyException();
    }

    @Test
    void enforcesActionAndOwnership() {
        var uploader = new AuthenticatedClient("uploader", false);
        when(repository.findByClientIdAndBucketName("uploader", "photos"))
                .thenReturn(Optional.of(new BucketPermission("uploader", "photos", Set.of(BucketAction.UPLOAD))));

        assertThatCode(() -> service.requireUpload(uploader, bucket)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireDelete(uploader, bucket))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.requireOwnerOrAdmin(uploader, bucket))
                .isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> service.requireOwnerOrAdmin(new AuthenticatedClient("owner", false), bucket))
                .doesNotThrowAnyException();
    }
}
