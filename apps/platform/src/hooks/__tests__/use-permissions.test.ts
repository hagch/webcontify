import { renderHook } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { usePermissions } from "../use-permissions";

vi.mock("@/generated/auth", () => ({
  useGetEffectivePermissions: vi.fn(),
}));

import { useGetEffectivePermissions } from "@/generated/auth";
const mockedUseGetEffectivePermissions = vi.mocked(useGetEffectivePermissions);

describe("usePermissions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns can() helper that checks specific permission", () => {
    mockedUseGetEffectivePermissions.mockReturnValue({
      data: {
        status: 200,
        data: {
          permissions: { user_management: ["reader", "inviter"] },
          isTenantAdmin: false,
        },
      },
      isLoading: false,
    } as any);

    const { result } = renderHook(() => usePermissions());
    expect(result.current.can("user_management", "reader")).toBe(true);
    expect(result.current.can("user_management", "editor")).toBe(false);
    expect(result.current.can("role_management", "reader")).toBe(false);
  });

  it("returns canAccess() helper that checks any permission on resource", () => {
    mockedUseGetEffectivePermissions.mockReturnValue({
      data: {
        status: 200,
        data: {
          permissions: { user_management: ["reader"] },
          isTenantAdmin: false,
        },
      },
      isLoading: false,
    } as any);

    const { result } = renderHook(() => usePermissions());
    expect(result.current.canAccess("user_management")).toBe(true);
    expect(result.current.canAccess("role_management")).toBe(false);
  });

  it("returns isLoading true while query is loading", () => {
    mockedUseGetEffectivePermissions.mockReturnValue({
      data: undefined,
      isLoading: true,
    } as any);

    const { result } = renderHook(() => usePermissions());
    expect(result.current.isLoading).toBe(true);
    expect(result.current.permissions).toEqual({});
  });

  it("returns empty permissions when no data", () => {
    mockedUseGetEffectivePermissions.mockReturnValue({
      data: undefined,
      isLoading: false,
    } as any);

    const { result } = renderHook(() => usePermissions());
    expect(result.current.can("user_management", "reader")).toBe(false);
    expect(result.current.canAccess("user_management")).toBe(false);
    expect(result.current.isTenantAdmin).toBe(false);
  });

  it("returns isTenantAdmin from response", () => {
    mockedUseGetEffectivePermissions.mockReturnValue({
      data: {
        status: 200,
        data: {
          permissions: {
            user_management: ["reader", "inviter", "editor", "deleter"],
          },
          isTenantAdmin: true,
        },
      },
      isLoading: false,
    } as any);

    const { result } = renderHook(() => usePermissions());
    expect(result.current.isTenantAdmin).toBe(true);
  });
});
