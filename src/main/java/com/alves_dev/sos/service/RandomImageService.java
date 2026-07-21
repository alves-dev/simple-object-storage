package com.alves_dev.sos.service;

import com.alves_dev.sos.exception.ContentNotFoundException;
import com.alves_dev.sos.exception.FileNotFoundException;
import com.alves_dev.sos.model.FileMetadata;
import com.alves_dev.sos.model.StorageStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
public class RandomImageService {

    private final MongoTemplate mongoTemplate;
    private final FileContentService contentService;
    private final FileAccessTrackingService trackingService;

    public RandomImageService(MongoTemplate mongoTemplate, FileContentService contentService,
                              FileAccessTrackingService trackingService) {
        this.mongoTemplate = mongoTemplate;
        this.contentService = contentService;
        this.trackingService = trackingService;
    }

    public RandomImage load(String bucketName) {
        for (int attempt = 0; attempt < 2; attempt++) {
            FileMetadata candidate = sample(bucketName);
            if (candidate == null) throw new FileNotFoundException("random-image");
            try {
                byte[] bytes = contentService.load(candidate);
                trackingService.record(candidate.getFileId());
                return new RandomImage(candidate, bytes);
            } catch (ContentNotFoundException exception) {
                if (attempt == 1) throw exception;
            }
        }
        throw new FileNotFoundException("random-image");
    }

    private FileMetadata sample(String bucketName) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("bucket").is(bucketName)
                        .and("isPublic").is(true)
                        .and("mimeType").regex("^image/")
                        .and("storageStatus").is(StorageStatus.AVAILABLE)),
                Aggregation.sample(1));
        return mongoTemplate.aggregate(aggregation, "files", FileMetadata.class)
                .getUniqueMappedResult();
    }

    public record RandomImage(FileMetadata metadata, byte[] bytes) {
    }
}
