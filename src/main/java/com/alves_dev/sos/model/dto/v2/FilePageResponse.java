package com.alves_dev.sos.model.dto.v2;

import java.util.List;

public record FilePageResponse(List<FileResponse> files, PageInfo pagination) {
}
