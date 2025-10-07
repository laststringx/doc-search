package com.enterprise.documentsearch.controller;

import com.enterprise.documentsearch.service.FileProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest(FileUploadController.class)
@DisplayName("FileUploadController Unit Tests")
class FileUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileProcessingService fileProcessingService;

    @Nested
    @DisplayName("Single File Upload Tests")
    class SingleFileUploadTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should upload single file successfully")
        void shouldUploadSingleFileSuccessfully() throws Exception {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.pdf",
                    MediaType.APPLICATION_PDF_VALUE,
                    "PDF content".getBytes()
            );

            Map<String, Object> response = Map.of(
                    "id", 1L,
                    "filename", "test.pdf",
                    "status", "uploaded",
                    "message", "File uploaded successfully"
            );

            when(fileProcessingService.processSingleFile(any(), eq("tenant_123")))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(multipart("/api/v1/upload/single")
                    .file(file)
                    .with(csrf())
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.filename").value("test.pdf"))
                    .andExpect(jsonPath("$.status").value("uploaded"))
                    .andExpect(jsonPath("$.message").value("File uploaded successfully"));

            verify(fileProcessingService).processSingleFile(any(), eq("tenant_123"));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should fail to upload large file")
        void shouldFailToUploadLargeFile() throws Exception {
            // Given
            MockMultipartFile largeFile = new MockMultipartFile(
                    "file",
                    "large.pdf",
                    MediaType.APPLICATION_PDF_VALUE,
                    new byte[11 * 1024 * 1024] // 11MB file
            );

            when(fileProcessingService.processSingleFile(any(), eq("tenant_123")))
                    .thenThrow(new RuntimeException("File size exceeds maximum limit"));

            // When & Then
            mockMvc.perform(multipart("/api/v1/upload/single")
                    .file(largeFile)
                    .with(csrf())
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("File size exceeds maximum limit"));

            verify(fileProcessingService).processSingleFile(any(), eq("tenant_123"));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should fail to upload unsupported file type")
        void shouldFailToUploadUnsupportedFileType() throws Exception {
            // Given
            MockMultipartFile unsupportedFile = new MockMultipartFile(
                    "file",
                    "test.exe",
                    "application/x-executable",
                    "Executable content".getBytes()
            );

            when(fileProcessingService.processSingleFile(any(), eq("tenant_123")))
                    .thenThrow(new RuntimeException("Unsupported file type"));

            // When & Then
            mockMvc.perform(multipart("/api/v1/upload/single")
                    .file(unsupportedFile)
                    .with(csrf())
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Unsupported file type"));

            verify(fileProcessingService).processSingleFile(any(), eq("tenant_123"));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should fail to upload empty file")
        void shouldFailToUploadEmptyFile() throws Exception {
            // Given
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file",
                    "empty.pdf",
                    MediaType.APPLICATION_PDF_VALUE,
                    new byte[0]
            );

            when(fileProcessingService.processSingleFile(any(), eq("tenant_123")))
                    .thenThrow(new RuntimeException("File is empty"));

            // When & Then
            mockMvc.perform(multipart("/api/v1/upload/single")
                    .file(emptyFile)
                    .with(csrf())
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("File is empty"));

            verify(fileProcessingService).processSingleFile(any(), eq("tenant_123"));
        }

        @Test
        @DisplayName("Should fail to upload without authentication")
        void shouldFailToUploadWithoutAuth() throws Exception {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.pdf",
                    MediaType.APPLICATION_PDF_VALUE,
                    "PDF content".getBytes()
            );

            // When & Then
            mockMvc.perform(multipart("/api/v1/upload/single")
                    .file(file))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(fileProcessingService);
        }
    }

    @Nested
    @DisplayName("Batch File Upload Tests")
    class BatchFileUploadTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should upload multiple files successfully")
        void shouldUploadMultipleFilesSuccessfully() throws Exception {
            // Given
            MockMultipartFile file1 = new MockMultipartFile(
                    "files",
                    "test1.pdf",
                    MediaType.APPLICATION_PDF_VALUE,
                    "PDF content 1".getBytes()
            );

            MockMultipartFile file2 = new MockMultipartFile(
                    "files",
                    "test2.txt",
                    MediaType.TEXT_PLAIN_VALUE,
                    "Text content 2".getBytes()
            );

            Map<String, Object> batchResponse = Map.of(
                    "totalFiles", 2,
                    "successCount", 2,
                    "failureCount", 0,
                    "results", Arrays.asList(
                            Map.of("filename", "test1.pdf", "status", "uploaded"),
                            Map.of("filename", "test2.txt", "status", "uploaded")
                    )
            );

            when(fileProcessingService.processBatchFiles(any(), eq("tenant_123")))
                    .thenReturn(batchResponse);

            // When & Then
            mockMvc.perform(multipart("/api/v1/upload/batch")
                    .file(file1)
                    .file(file2)
                    .with(csrf())
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalFiles").value(2))
                    .andExpect(jsonPath("$.successCount").value(2))
                    .andExpect(jsonPath("$.failureCount").value(0))
                    .andExpect(jsonPath("$.results").isArray())
                    .andExpect(jsonPath("$.results.length()").value(2));

            verify(fileProcessingService).processBatchFiles(any(), eq("tenant_123"));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should handle partial batch upload failure")
        void shouldHandlePartialBatchUploadFailure() throws Exception {
            // Given
            MockMultipartFile file1 = new MockMultipartFile(
                    "files",
                    "test1.pdf",
                    MediaType.APPLICATION_PDF_VALUE,
                    "PDF content 1".getBytes()
            );

            MockMultipartFile file2 = new MockMultipartFile(
                    "files",
                    "large.pdf",
                    MediaType.APPLICATION_PDF_VALUE,
                    new byte[11 * 1024 * 1024] // 11MB file
            );

            Map<String, Object> batchResponse = Map.of(
                    "totalFiles", 2,
                    "successCount", 1,
                    "failureCount", 1,
                    "results", Arrays.asList(
                            Map.of("filename", "test1.pdf", "status", "uploaded"),
                            Map.of("filename", "large.pdf", "status", "failed", "error", "File too large")
                    )
            );

            when(fileProcessingService.processBatchFiles(any(), eq("tenant_123")))
                    .thenReturn(batchResponse);

            // When & Then
            mockMvc.perform(multipart("/api/v1/upload/batch")
                    .file(file1)
                    .file(file2)
                    .with(csrf())
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalFiles").value(2))
                    .andExpect(jsonPath("$.successCount").value(1))
                    .andExpect(jsonPath("$.failureCount").value(1));

            verify(fileProcessingService).processBatchFiles(any(), eq("tenant_123"));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should fail batch upload with too many files")
        void shouldFailBatchUploadWithTooManyFiles() throws Exception {
            // Given
            when(fileProcessingService.processBatchFiles(any(), eq("tenant_123")))
                    .thenThrow(new RuntimeException("Too many files in batch"));

            // When & Then
            mockMvc.perform(multipart("/api/v1/upload/batch")
                    .file(new MockMultipartFile("files", "file1.pdf", MediaType.APPLICATION_PDF_VALUE, "content1".getBytes()))
                    .file(new MockMultipartFile("files", "file2.pdf", MediaType.APPLICATION_PDF_VALUE, "content2".getBytes()))
                    .file(new MockMultipartFile("files", "file3.pdf", MediaType.APPLICATION_PDF_VALUE, "content3".getBytes()))
                    .file(new MockMultipartFile("files", "file4.pdf", MediaType.APPLICATION_PDF_VALUE, "content4".getBytes()))
                    .file(new MockMultipartFile("files", "file5.pdf", MediaType.APPLICATION_PDF_VALUE, "content5".getBytes()))
                    .file(new MockMultipartFile("files", "file6.pdf", MediaType.APPLICATION_PDF_VALUE, "content6".getBytes()))
                    .with(csrf())
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Too many files in batch"));

            verify(fileProcessingService).processBatchFiles(any(), eq("tenant_123"));
        }
    }

    @Nested
    @DisplayName("Supported File Types Tests")
    class SupportedFileTypesTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("Should return supported file types")
        void shouldReturnSupportedFileTypes() throws Exception {
            // Given
            when(fileProcessingService.getSupportedFileTypes())
                    .thenReturn(Arrays.asList("PDF", "DOC", "DOCX", "TXT"));

            // When & Then
            mockMvc.perform(get("/api/v1/upload/types")
                    .header("X-Tenant-ID", "tenant_123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.supportedTypes").isArray())
                    .andExpect(jsonPath("$.supportedTypes.length()").value(4))
                    .andExpect(jsonPath("$.supportedTypes[0]").value("PDF"))
                    .andExpect(jsonPath("$.supportedTypes[1]").value("DOC"))
                    .andExpect(jsonPath("$.supportedTypes[2]").value("DOCX"))
                    .andExpect(jsonPath("$.supportedTypes[3]").value("TXT"));

            verify(fileProcessingService).getSupportedFileTypes();
        }

        @Test
        @DisplayName("Should return supported file types without authentication")
        void shouldReturnSupportedFileTypesWithoutAuth() throws Exception {
            // Given
            when(fileProcessingService.getSupportedFileTypes())
                    .thenReturn(Arrays.asList("PDF", "DOC", "DOCX", "TXT"));

            // When & Then
            mockMvc.perform(get("/api/v1/upload/types"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.supportedTypes").isArray());

            verify(fileProcessingService).getSupportedFileTypes();
        }
    }
}