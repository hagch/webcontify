import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import { useTenantConfigFacade } from "../use-tenant-config-facade";

const mockConfig = {
  branding: { primaryColor: "#3b82f6", secondaryColor: "#64748b", logoStorageKey: null, faviconStorageKey: null },
  email: { smtpHost: "smtp.example.com", smtpPort: 587, smtpUser: "user", fromAddress: "no-reply@example.com", fromName: "Test" },
  domain: { customDomain: "app.example.com", sslStatus: "ACTIVE" },
};

const mockBrandingMutateAsync = vi.fn();
const mockEmailMutateAsync = vi.fn();
const mockDomainMutateAsync = vi.fn();
const mockTestEmailMutateAsync = vi.fn();

vi.mock("@/generated/tenant", () => ({
  useGetAdminTenantConfig: vi.fn(() => ({
    data: { data: mockConfig, status: 200 },
    isLoading: false,
    error: null,
  })),
  useUpdateAdminBrandingConfig: vi.fn(() => ({
    mutateAsync: mockBrandingMutateAsync,
    isPending: false,
  })),
  useUpdateAdminEmailConfig: vi.fn(() => ({
    mutateAsync: mockEmailMutateAsync,
    isPending: false,
  })),
  useUpdateAdminDomainConfig: vi.fn(() => ({
    mutateAsync: mockDomainMutateAsync,
    isPending: false,
  })),
  useTestAdminEmailConfig: vi.fn(() => ({
    mutateAsync: mockTestEmailMutateAsync,
    isPending: false,
  })),
  getGetAdminTenantConfigQueryKey: vi.fn((tenantId: string) => ["getAdminTenantConfig", tenantId]),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe("useTenantConfigFacade", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns config from the query", () => {
    const { result } = renderHook(() => useTenantConfigFacade("test-tenant-id"), {
      wrapper: createWrapper(),
    });

    expect(result.current.config).toEqual(mockConfig);
    expect(result.current.isLoading).toBe(false);
  });

  it("returns undefined config when loading", async () => {
    const { useGetAdminTenantConfig } = await import("@/generated/tenant");
    vi.mocked(useGetAdminTenantConfig).mockReturnValueOnce({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useGetAdminTenantConfig>);

    const { result } = renderHook(() => useTenantConfigFacade("test-tenant-id"), {
      wrapper: createWrapper(),
    });

    expect(result.current.config).toBeUndefined();
    expect(result.current.isLoading).toBe(true);
  });

  it("updateBranding calls mutation with tenantId and data", async () => {
    const { result } = renderHook(() => useTenantConfigFacade("test-tenant-id"), {
      wrapper: createWrapper(),
    });

    const brandingData = { primaryColor: "#ff0000", secondaryColor: "#00ff00" };

    await act(async () => {
      await result.current.updateBranding(brandingData);
    });

    expect(mockBrandingMutateAsync).toHaveBeenCalledWith({
      tenantId: "test-tenant-id",
      data: brandingData,
    });
  });

  it("updateEmail calls mutation with tenantId and data", async () => {
    const { result } = renderHook(() => useTenantConfigFacade("test-tenant-id"), {
      wrapper: createWrapper(),
    });

    const emailData = {
      smtpHost: "smtp.new.com",
      smtpPort: 465,
      smtpUser: "newuser",
      fromAddress: "info@new.com",
      fromName: "New",
    };

    await act(async () => {
      await result.current.updateEmail(emailData);
    });

    expect(mockEmailMutateAsync).toHaveBeenCalledWith({
      tenantId: "test-tenant-id",
      data: emailData,
    });
  });

  it("updateDomain calls mutation with tenantId and data", async () => {
    const { result } = renderHook(() => useTenantConfigFacade("test-tenant-id"), {
      wrapper: createWrapper(),
    });

    const domainData = { customDomain: "new.example.com" };

    await act(async () => {
      await result.current.updateDomain(domainData);
    });

    expect(mockDomainMutateAsync).toHaveBeenCalledWith({
      tenantId: "test-tenant-id",
      data: domainData,
    });
  });

  it("testEmail calls mutation with tenantId and data", async () => {
    const { result } = renderHook(() => useTenantConfigFacade("test-tenant-id"), {
      wrapper: createWrapper(),
    });

    const testData = { recipientEmail: "test@example.com" };

    await act(async () => {
      await result.current.testEmail(testData);
    });

    expect(mockTestEmailMutateAsync).toHaveBeenCalledWith({
      tenantId: "test-tenant-id",
      data: testData,
    });
  });
});
