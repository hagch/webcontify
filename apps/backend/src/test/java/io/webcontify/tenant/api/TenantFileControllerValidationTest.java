package io.webcontify.tenant.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.service.TenantFileService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfiguration.class)
class TenantFileControllerValidationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    TenantFileService tenantFileService;

    @Autowired
    MockMvc mockMvc;

    private static final UUID TEST_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void acceptsSvgUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.svg", "image/svg+xml", "<svg/>".getBytes());

        mockMvc.perform(multipart("/api/v1/tenant/files")
                        .file(file)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsOversizedFile() throws Exception {
        byte[] oversizedContent = new byte[2 * 1024 * 1024 + 1]; // 2MB + 1 byte
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.png", "image/png", oversizedContent);

        mockMvc.perform(multipart("/api/v1/tenant/files")
                        .file(file)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsPngUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.png", "image/png", "png-content".getBytes());

        when(tenantFileService.uploadFile(eq(TEST_TENANT_ID), any(), any(), eq("image/png"), eq(false)))
                .thenReturn("config/logo.png");

        mockMvc.perform(multipart("/api/v1/tenant/files")
                        .file(file)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID))
                .andExpect(status().isCreated());
    }

    @Test
    void acceptsJpegUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.jpg", "image/jpeg", "jpeg-content".getBytes());

        when(tenantFileService.uploadFile(eq(TEST_TENANT_ID), any(), any(), eq("image/jpeg"), eq(false)))
                .thenReturn("config/logo.jpg");

        mockMvc.perform(multipart("/api/v1/tenant/files")
                        .file(file)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID))
                .andExpect(status().isCreated());
    }

    @Test
    void acceptsIcoUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "favicon.ico", "image/x-icon", "ico-content".getBytes());

        when(tenantFileService.uploadFile(eq(TEST_TENANT_ID), any(), any(), eq("image/x-icon"), eq(false)))
                .thenReturn("config/favicon.ico");

        mockMvc.perform(multipart("/api/v1/tenant/files")
                        .file(file)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsFileWithoutContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.png", null, "png-content".getBytes());

        mockMvc.perform(multipart("/api/v1/tenant/files")
                        .file(file)
                        .header(TestSecurityConfiguration.TEST_TENANT_ID_HEADER, TEST_TENANT_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPlatformAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.png", "image/png", "png-content".getBytes());

        // Kein X-Test-Tenant-Id Header -> Platform Admin wird injiziert
        mockMvc.perform(multipart("/api/v1/tenant/files")
                        .file(file))
                .andExpect(status().isForbidden());
    }
}
