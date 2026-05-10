package com.resumeai.export.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void writeLoadSizeUrlResolveAndDeleteWorkTogether() throws Exception {
        FileStorageService service = new FileStorageService();
        ReflectionTestUtils.setField(service, "storageDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "downloadBaseUrl", "http://localhost:8086");

        Path file = service.write("job-1", "resume.json", "{\"ok\":true}".getBytes());

        assertTrue(Files.exists(file));
        assertTrue(service.loadAsResource("job-1", "json").exists());
        assertEquals(tempDir.resolve("job-1").resolve("resume.json"), service.resolveFilePath("job-1", "json"));
        assertEquals("http://localhost:8086/api/v1/exports/job-1/file", service.buildDownloadUrl("job-1"));
        assertEquals(0, service.fileSizeKb(file));

        service.deleteJobFiles("job-1");
        assertTrue(Files.notExists(tempDir.resolve("job-1")));
    }

    @Test
    void missingFileReturnsZeroSizeAndLoadThrows() {
        FileStorageService service = new FileStorageService();
        ReflectionTestUtils.setField(service, "storageDir", tempDir.toString());

        assertEquals(0, service.fileSizeKb(tempDir.resolve("missing.pdf")));
        assertThrows(IOException.class, () -> service.loadAsResource("missing", "pdf"));
        service.deleteJobFiles("missing");
    }
}

