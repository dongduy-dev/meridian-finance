package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.domain.model.DocumentUploadRules;
import com.meridian.platform.document.infrastructure.adapter.out.storage.LocalFilesystemDocumentStorageAdapter;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.servlet.autoconfigure.MultipartAutoConfiguration;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MultipartUploadContractTest {
    @TempDir Path root;

    @Test
    void realTomcatUsesProductionLimitsAndAcceptsTenMiBIncludingMultipartOverhead() throws Exception {
        // Import the actual production file; do not repeat the limit in a test property.
        try (var context = new SpringApplicationBuilder(TransportApplication.class)
                .run("--server.port=0", "--spring.main.banner-mode=off")) {
            context.getBean(Probe.class).storage = new LocalFilesystemDocumentStorageAdapter(root.toString());
            var multipart = context.getBean(jakarta.servlet.MultipartConfigElement.class);
            assertEquals(DocumentUploadRules.MAX_BYTE_SIZE, multipart.getMaxFileSize());
            assertEquals(11L * 1024 * 1024, multipart.getMaxRequestSize());
            var server = ((org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext) context).getWebServer();
            try (var client = HttpClient.newHttpClient()) {
                for (int size : new int[]{2 * 1024 * 1024, (int) DocumentUploadRules.MAX_BYTE_SIZE}) {
                    var response = upload(client, server.getPort(), size);
                    assertEquals(200, response.statusCode(), response.body());
                    assertEquals(Integer.toString(size), response.body());
                }
                var oversized = upload(client, server.getPort(), (int) DocumentUploadRules.MAX_BYTE_SIZE + 1);
                assertEquals(413, oversized.statusCode(), oversized.body());
                assertTrue(oversized.body().contains("INVALID_DOCUMENT_UPLOAD"));
            }
        }
    }

    private static HttpResponse<String> upload(HttpClient client, int port, int size) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--contract-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fictional.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        byte[] file = new byte[size];
        System.arraycopy("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), 0, file, 0, 9);
        body.write(file);
        body.write("\r\n--contract-boundary--\r\n".getBytes(StandardCharsets.US_ASCII));
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/upload-probe"))
                .header("Content-Type", "multipart/form-data; boundary=contract-boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @PropertySource("file:src/main/resources/application.properties")
    @ImportAutoConfiguration({TomcatServletWebServerAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class, MultipartAutoConfiguration.class})
    @Import(GlobalExceptionHandler.class)
    static class TransportApplication {
        @Bean Probe probe() { return new Probe(); }
    }

    @RestController
    @TestComponent
    static class Probe {
        LocalFilesystemDocumentStorageAdapter storage;
        @PostMapping("/upload-probe")
        String upload(@RequestParam("file") MultipartFile file) throws Exception {
            var staged = storage.stage(file.getInputStream(), file.getContentType(), file.getOriginalFilename());
            try { return Long.toString(staged.byteSize()); } finally { storage.discardStaged(staged); }
        }
    }
}
