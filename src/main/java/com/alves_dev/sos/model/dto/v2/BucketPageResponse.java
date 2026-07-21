package com.alves_dev.sos.model.dto.v2;

import java.util.List;

public record BucketPageResponse(List<BucketResponse> buckets, PageInfo pagination) {
}
