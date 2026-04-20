package io.webcontify.shared.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.webcontify.shared.model.TenantInformation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TenantResolutionFilterTest {

    @Mock
    private TenantSlugResolver tenantSlugResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private TenantResolutionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TenantResolutionFilter(tenantSlugResolver);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughWhenNoAuthentication() throws Exception {
        // kein Authentication im SecurityContext -> permitAll Endpoints
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tenantSlugResolver, never()).resolveTenantIdBySlug(anyString());
    }

    @Test
    void shouldPassThroughForPlatformAdmin() throws Exception {
        // Platform Admin braucht keinen Subdomain-Check
        TenantInformation tenantInfo = new TenantInformation(null, true);
        TenantAwareAuthentication auth = new TenantAwareAuthentication(
                tenantInfo, UUID.randomUUID(), List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tenantSlugResolver, never()).resolveTenantIdBySlug(anyString());
    }

    @Test
    void shouldPassThroughForTenantUserWithMatchingSubdomain() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantInformation tenantInfo = new TenantInformation(tenantId, false);
        TenantAwareAuthentication auth = new TenantAwareAuthentication(
                tenantInfo, UUID.randomUUID(), List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(request.getHeader("Host")).thenReturn("acme.api.localhost");

        when(tenantSlugResolver.resolveTenantIdBySlug("acme"))
                .thenReturn(Optional.of(tenantId));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturn403WhenSubdomainDoesNotMatchJwtTenant() throws Exception {
        UUID jwtTenantId = UUID.randomUUID();
        UUID dbTenantId = UUID.randomUUID();
        TenantInformation tenantInfo = new TenantInformation(jwtTenantId, false);
        TenantAwareAuthentication auth = new TenantAwareAuthentication(
                tenantInfo, UUID.randomUUID(), List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(request.getHeader("Host")).thenReturn("acme.api.localhost");

        when(tenantSlugResolver.resolveTenantIdBySlug("acme"))
                .thenReturn(Optional.of(dbTenantId));

        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/problem+json");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldReturn403WhenTenantSlugNotFoundInDb() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantInformation tenantInfo = new TenantInformation(tenantId, false);
        TenantAwareAuthentication auth = new TenantAwareAuthentication(
                tenantInfo, UUID.randomUUID(), List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(request.getHeader("Host")).thenReturn("unknown.api.localhost");

        when(tenantSlugResolver.resolveTenantIdBySlug("unknown"))
                .thenReturn(Optional.empty());

        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/problem+json");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldReturn403ForTenantUserWithoutSubdomain() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantInformation tenantInfo = new TenantInformation(tenantId, false);
        TenantAwareAuthentication auth = new TenantAwareAuthentication(
                tenantInfo, UUID.randomUUID(), List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(request.getHeader("Host")).thenReturn("api.localhost");

        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/problem+json");
        verify(filterChain, never()).doFilter(request, response);
        verify(tenantSlugResolver, never()).resolveTenantIdBySlug(anyString());
    }

    @Test
    void shouldReturn403ForTenantUserWhenHostHeaderIsMissing() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantInformation tenantInfo = new TenantInformation(tenantId, false);
        TenantAwareAuthentication auth = new TenantAwareAuthentication(
                tenantInfo, UUID.randomUUID(), List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(request.getHeader("Host")).thenReturn(null);

        StringWriter stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/problem+json");
        verify(filterChain, never()).doFilter(request, response);
    }

}
