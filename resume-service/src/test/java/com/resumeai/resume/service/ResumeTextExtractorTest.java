package com.resumeai.resume.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = new ResumeTextExtractor();

    @Test
    void extractsTextFilesAndTrimsContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "  Java developer resume  ".getBytes());

        assertEquals("Java developer resume", extractor.extract(file));
    }

    @Test
    void acceptsMarkdownAndTextContentTypeWithoutExtension() {
        MockMultipartFile markdown = new MockMultipartFile(
                "file", "resume.md", "application/octet-stream", "# Resume".getBytes());
        MockMultipartFile textContentType = new MockMultipartFile(
                "file", "resume", "text/plain", "Plain resume".getBytes());

        assertEquals("# Resume", extractor.extract(markdown));
        assertEquals("Plain resume", extractor.extract(textContentType));
    }

    @Test
    void rejectsMissingLargeUnsupportedAndBlankFiles() {
        assertThrows(ResponseStatusException.class, () -> extractor.extract(null));
        assertThrows(ResponseStatusException.class, () -> extractor.extract(
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])));
        assertThrows(ResponseStatusException.class, () -> extractor.extract(
                new MockMultipartFile("file", "large.txt", "text/plain", new byte[8 * 1024 * 1024 + 1])));
        assertThrows(ResponseStatusException.class, () -> extractor.extract(
                new MockMultipartFile("file", "resume.exe", "application/octet-stream", "text".getBytes())));
        assertThrows(ResponseStatusException.class, () -> extractor.extract(
                new MockMultipartFile("file", "blank.txt", "text/plain", "   ".getBytes())));
    }

    @Test
    void wrapsIoFailuresAsBadRequest() throws IOException {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        org.mockito.Mockito.when(file.isEmpty()).thenReturn(false);
        org.mockito.Mockito.when(file.getSize()).thenReturn(4L);
        org.mockito.Mockito.when(file.getOriginalFilename()).thenReturn("resume.txt");
        org.mockito.Mockito.when(file.getBytes()).thenThrow(new IOException("disk"));

        assertThrows(ResponseStatusException.class, () -> extractor.extract(file));
    }
}
