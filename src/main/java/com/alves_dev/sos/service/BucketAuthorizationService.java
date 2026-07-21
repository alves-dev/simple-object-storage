package com.alves_dev.sos.service;

import com.alves_dev.sos.exception.AccessDeniedException;
import com.alves_dev.sos.exception.BucketDisabledException;
import com.alves_dev.sos.model.Bucket;
import com.alves_dev.sos.model.BucketAction;
import com.alves_dev.sos.repository.BucketPermissionRepository;
import com.alves_dev.sos.security.AuthenticatedClient;
import org.springframework.stereotype.Service;

@Service
public class BucketAuthorizationService {

    private final BucketPermissionRepository permissionRepository;

    public BucketAuthorizationService(BucketPermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public void requireUpload(AuthenticatedClient client, Bucket bucket) {
        if (!bucket.isEnabled()) {
            throw new BucketDisabledException(bucket.getName());
        }
        requireAction(client, bucket, BucketAction.UPLOAD);
    }

    public void requireDelete(AuthenticatedClient client, Bucket bucket) {
        requireAction(client, bucket, BucketAction.DELETE);
    }

    public void requireOwnerOrAdmin(AuthenticatedClient client, Bucket bucket) {
        if (!client.admin() && !bucket.getCreatedByClientId().equals(client.clientId())) {
            throw new AccessDeniedException("Only the bucket owner or an administrator may perform this operation");
        }
    }

    public void requireAnyPermissionOrAdmin(AuthenticatedClient client, Bucket bucket) {
        if (!client.admin() && permissionRepository
                .findByClientIdAndBucketName(client.clientId(), bucket.getName()).isEmpty()) {
            throw new AccessDeniedException("Client has no permission for this bucket");
        }
    }

    private void requireAction(AuthenticatedClient client, Bucket bucket, BucketAction action) {
        if (client.admin()) {
            return;
        }
        boolean allowed = permissionRepository.findByClientIdAndBucketName(client.clientId(), bucket.getName())
                .map(permission -> permission.getActions().contains(action))
                .orElse(false);
        if (!allowed) {
            throw new AccessDeniedException("Client does not have " + action + " permission for this bucket");
        }
    }
}
