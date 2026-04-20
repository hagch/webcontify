import { describe, it, expect, vi } from "vitest";
import { renderHook } from "@testing-library/react";

vi.mock("next-auth/react", () => ({
  useSession: vi.fn(),
}));

import { useAuth } from "../use-auth";
import { useSession } from "next-auth/react";

const mockedUseSession = vi.mocked(useSession);

describe("useAuth", () => {
  it("returns unauthenticated state when no session", () => {
    mockedUseSession.mockReturnValue({
      data: null,
      status: "unauthenticated",
      update: vi.fn(),
    });

    const { result } = renderHook(() => useAuth());

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.isPlatformAdmin).toBe(false);
    expect(result.current.user).toBeNull();
    expect(result.current.isLoading).toBe(false);
  });

  it("returns loading state", () => {
    mockedUseSession.mockReturnValue({
      data: null,
      status: "loading",
      update: vi.fn(),
    });

    const { result } = renderHook(() => useAuth());

    expect(result.current.isLoading).toBe(true);
  });

  it("returns authenticated user", () => {
    mockedUseSession.mockReturnValue({
      data: {
        user: { name: "Test User", email: "test@example.com" },
        accessToken: "test-token",
        expires: "2099-01-01",
      },
      status: "authenticated",
      update: vi.fn(),
    });

    const { result } = renderHook(() => useAuth());

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user?.name).toBe("Test User");
    expect(result.current.user?.email).toBe("test@example.com");
  });

  it("detects platform admin from access token", () => {
    const payload = btoa(
      JSON.stringify({ realm_access: { roles: ["PLATFORM_ADMIN"] } })
    );
    const fakeJwt = `header.${payload}.signature`;

    mockedUseSession.mockReturnValue({
      data: {
        user: { name: "Admin" },
        accessToken: fakeJwt,
        expires: "2099-01-01",
      },
      status: "authenticated",
      update: vi.fn(),
    });

    const { result } = renderHook(() => useAuth());

    expect(result.current.isPlatformAdmin).toBe(true);
  });

  it("returns isPlatformAdmin false for non-admin tokens", () => {
    const payload = btoa(
      JSON.stringify({ realm_access: { roles: ["user"] } })
    );
    const fakeJwt = `header.${payload}.signature`;

    mockedUseSession.mockReturnValue({
      data: {
        user: { name: "User" },
        accessToken: fakeJwt,
        expires: "2099-01-01",
      },
      status: "authenticated",
      update: vi.fn(),
    });

    const { result } = renderHook(() => useAuth());

    expect(result.current.isPlatformAdmin).toBe(false);
  });
});
