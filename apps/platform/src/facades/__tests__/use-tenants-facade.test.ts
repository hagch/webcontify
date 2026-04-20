import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import {
  useTenantListFacade,
  useTenantDetailFacade,
} from "../use-tenants-facade";
import type { TenantResponse, TenantStatus } from "@/generated/tenant";

const mockTenant: TenantResponse = {
  id: "tenant-1",
  name: "Test Tenant",
  slug: "test-tenant",
  status: "ACTIVE" as TenantStatus,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const mockCreateMutateAsync = vi.fn();
const mockUpdateMutateAsync = vi.fn();
const mockStatusMutateAsync = vi.fn();
const mockDeleteMutateAsync = vi.fn();

vi.mock("@/generated/tenant", () => ({
  useListTenants: vi.fn(() => ({
    data: { data: { items: [mockTenant], pageInfo: { page: 0, size: 20, totalElements: 1, totalPages: 1 } }, status: 200 },
    isLoading: false,
    error: null,
  })),
  useCreateTenant: vi.fn(() => ({
    mutateAsync: mockCreateMutateAsync,
    isPending: false,
  })),
  useGetTenant: vi.fn(() => ({
    data: { data: mockTenant, status: 200 },
    isLoading: false,
    error: null,
  })),
  useUpdateTenant: vi.fn(() => ({
    mutateAsync: mockUpdateMutateAsync,
    isPending: false,
  })),
  useUpdateTenantStatus: vi.fn(() => ({
    mutateAsync: mockStatusMutateAsync,
    isPending: false,
  })),
  useDeleteTenant: vi.fn(() => ({
    mutateAsync: mockDeleteMutateAsync,
    isPending: false,
  })),
  getListTenantsQueryKey: vi.fn(() => ["listTenants"]),
  getGetTenantQueryKey: vi.fn((id: string) => ["getTenant", id]),
  TenantStatus: {
    ACTIVE: "ACTIVE",
    INACTIVE: "INACTIVE",
    SUSPENDED: "SUSPENDED",
  },
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe("useTenantListFacade", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns tenants from the query", () => {
    const { result } = renderHook(() => useTenantListFacade(), {
      wrapper: createWrapper(),
    });

    expect(result.current.tenants).toEqual([mockTenant]);
    expect(result.current.isLoading).toBe(false);
  });

  it("returns pageInfo from the query", () => {
    const { result } = renderHook(() => useTenantListFacade(), {
      wrapper: createWrapper(),
    });

    expect(result.current.pageInfo).toEqual({
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
  });

  it("returns empty array when no data", async () => {
    const { useListTenants } = await import("@/generated/tenant");
    vi.mocked(useListTenants).mockReturnValueOnce({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useListTenants>);

    const { result } = renderHook(() => useTenantListFacade(), {
      wrapper: createWrapper(),
    });

    expect(result.current.tenants).toEqual([]);
    expect(result.current.isLoading).toBe(true);
  });

  it("createTenant calls mutation with data", async () => {
    const { result } = renderHook(() => useTenantListFacade(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.createTenant({ name: "New", slug: "new" });
    });

    expect(mockCreateMutateAsync).toHaveBeenCalledWith({
      data: { name: "New", slug: "new" },
    });
  });
});

describe("useTenantDetailFacade", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns tenant from the query", () => {
    const { result } = renderHook(() => useTenantDetailFacade("tenant-1"), {
      wrapper: createWrapper(),
    });

    expect(result.current.tenant).toEqual(mockTenant);
    expect(result.current.isLoading).toBe(false);
  });

  it("updateTenant calls mutation with tenantId and data", async () => {
    const { result } = renderHook(() => useTenantDetailFacade("tenant-1"), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.updateTenant({ name: "Updated", slug: "updated" });
    });

    expect(mockUpdateMutateAsync).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      data: { name: "Updated", slug: "updated" },
    });
  });

  it("updateStatus calls status mutation", async () => {
    const { result } = renderHook(() => useTenantDetailFacade("tenant-1"), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.updateStatus("SUSPENDED" as TenantStatus);
    });

    expect(mockStatusMutateAsync).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      data: { status: "SUSPENDED" },
    });
  });

  it("deleteTenant calls delete mutation", async () => {
    const { result } = renderHook(() => useTenantDetailFacade("tenant-1"), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.deleteTenant();
    });

    expect(mockDeleteMutateAsync).toHaveBeenCalledWith({
      tenantId: "tenant-1",
    });
  });
});
