package com.resumeai.export.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages the local filesystem store for exported resume files.
 *
 * <p>Directory layout:
 * <pre>
 *   {storage-dir}/
 *     {jobId}/
 *       resume.pdf    (or resume.docx / resume.json)
 * </pre>
 *
 * <p>The download URL returned to the client is:
 * <pre>
 *   {download-base-url}/api/v1/exports/{jobId}/file
 * </pre>
 */
@Service
@Slf4j
public class FileStorageService {

    @Value("${app.export.storage-dir:./exports}")
    private String storageDir;

    @Value("${app.export.download-base-url:http://localhost:8086}")
    private String downloadBaseUrl;

    /**
     * Build the public download URL for a job.
     * The actual file serving is handled by {@link com.resumeai.export.controller.ExportController}.
     */
    public String buildDownloadUrl(String jobId) {
        return downloadBaseUrl + "/api/v1/exports/" + jobId + "/file";
    }

    /**
     * Load a file as a Spring {@link Resource} for streaming to the client.
     *
     * @param jobId     export job ID
     * @param extension file extension without dot (pdf, docx, json)
     * @return Spring Resource ready to be returned from a controller
     * @throws IOException if the file does not exist or cannot be read
     */
    public Resource loadAsResource(String jobId, String extension) throws IOException {
        Path filePath = resolveFilePath(jobId, extension);
        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("File not found or unreadable: " + filePath);
        }
        return resource;
    }

    /**
     * Delete all files for a given job (entire job sub-directory).
     */
    public void deleteJobFiles(String jobId) {
        Path dir = Paths.get(storageDir, jobId);
        if (Files.exists(dir)) {
            try {
                Files.walk(dir)
                     .sorted(java.util.Comparator.reverseOrder())
                     .forEach(p -> {
                         try { Files.delete(p); } catch (IOException e) {
                             log.warn("Could not delete {}: {}", p, e.getMessage());
                         }
                     });
            } catch (IOException e) {
                log.error("Error deleting export dir {}: {}", dir, e.getMessage());
            }
        }
    }

    /** Write raw bytes (e.g. JSON) directly to the job directory. */
    public Path write(String jobId, String filename, byte[] bytes) throws IOException {
        Path dir = Paths.get(storageDir, jobId);
        Files.createDirectories(dir);
        Path file = dir.resolve(filename);
        Files.write(file, bytes);
        return file;
    }

    /** Resolve file path for a given job + extension. */
    public Path resolveFilePath(String jobId, String extension) {
        return Paths.get(storageDir, jobId, "resume." + extension);
    }

    /** Return approximate file size in KB (0 if file missing). */
    public int fileSizeKb(Path filePath) {
        try {
            return (int) (Files.size(filePath) / 1024);
        } catch (IOException e) {
            return 0;
        }
    }
}
