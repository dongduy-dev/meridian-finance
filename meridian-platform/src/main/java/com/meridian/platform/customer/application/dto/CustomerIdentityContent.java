package com.meridian.platform.customer.application.dto;

import java.io.InputStream;

public record CustomerIdentityContent(String filename, String mimeType, long byteSize, InputStream content) {}
