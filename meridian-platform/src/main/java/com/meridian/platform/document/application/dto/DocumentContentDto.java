package com.meridian.platform.document.application.dto;

import java.io.InputStream;

public record DocumentContentDto(
        String originalFilename,
        String detectedMimeType,
        long byteSize,
        InputStream content
) {
}
