package com.alves_dev.sos.service;

import com.alves_dev.sos.config.FileAccessTrackingConfig;
import com.alves_dev.sos.model.FileMetadata;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileAccessTrackingService {

    private static final Logger log = LoggerFactory.getLogger(FileAccessTrackingService.class);
    private final ConcurrentHashMap<String, PendingAccess> pending = new ConcurrentHashMap<>();
    private final MongoTemplate mongoTemplate;
    private final FileAccessTrackingConfig config;

    public FileAccessTrackingService(MongoTemplate mongoTemplate, FileAccessTrackingConfig config) {
        this.mongoTemplate = mongoTemplate;
        this.config = config;
    }

    public void record(String fileId) {
        LocalDate today = LocalDate.now();
        pending.merge(fileId, new PendingAccess(1, today),
                (current, added) -> new PendingAccess(current.count() + 1, today));
    }

    @Scheduled(fixedDelayString = "${file-access.flush-interval:1m}")
    public void flush() {
        for (Map.Entry<String, PendingAccess> entry : pending.entrySet()) {
            String fileId = entry.getKey();
            PendingAccess access = entry.getValue();
            if (!pending.remove(fileId, access)) continue;
            try {
                persist(fileId, access);
            } catch (RuntimeException exception) {
                pending.merge(fileId, access, (current, failed) ->
                        new PendingAccess(current.count() + failed.count(),
                                current.lastAccessDate().isAfter(failed.lastAccessDate())
                                        ? current.lastAccessDate() : failed.lastAccessDate()));
                log.warn("Access counter flush failed for fileId={}", fileId);
            }
        }
    }

    private void persist(String fileId, PendingAccess access) {
        FileMetadata file = mongoTemplate.findOne(Query.query(Criteria.where("fileId").is(fileId)), FileMetadata.class);
        if (file == null) return;
        LocalDate start = file.getRecentAccessWindowStart();
        boolean newWindow = start == null || start.plusDays(config.recentWindowDays()).isBefore(access.lastAccessDate());
        Update update = new Update().inc("directAccessCount", access.count())
                .set("lastDirectAccessDate", access.lastAccessDate());
        if (newWindow) {
            update.set("recentAccessWindowStart", access.lastAccessDate())
                    .set("recentDirectAccessCount", access.count());
        } else {
            update.inc("recentDirectAccessCount", access.count());
        }
        mongoTemplate.updateFirst(Query.query(Criteria.where("fileId").is(fileId)), update, FileMetadata.class);
    }

    @PreDestroy
    public void flushOnShutdown() {
        flush();
    }

    record PendingAccess(long count, LocalDate lastAccessDate) {
    }
}
