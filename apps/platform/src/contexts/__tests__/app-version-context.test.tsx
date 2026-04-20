import { describe, it, expect } from "vitest";
import { renderHook, act } from "@testing-library/react";
import type { AppResponse, AppVersionResponse } from "@/generated/app";
import { AppVersionProvider, useAppVersion } from "../app-version-context";

function makeApp(overrides: Partial<AppResponse> = {}): AppResponse {
  return {
    id: "app-1",
    name: "Test App",
    slug: "test-app",
    status: "DRAFT",
    hasDraft: true,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function makeVersion(id: string, overrides: Partial<AppVersionResponse> = {}): AppVersionResponse {
  return {
    id,
    appId: "app-1",
    version: "1.0.0",
    publishedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function wrapper(app: AppResponse, versions: AppVersionResponse[]) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <AppVersionProvider app={app} versions={versions}>
        {children}
      </AppVersionProvider>
    );
  };
}

describe("AppVersionProvider", () => {
  it("defaults to draft when hasDraft is true", () => {
    const app = makeApp({ hasDraft: true });
    const versions = [makeVersion("v1")];
    const { result } = renderHook(() => useAppVersion(), {
      wrapper: wrapper(app, versions),
    });

    expect(result.current.selectedVersion).toBe("draft");
    expect(result.current.isDraft).toBe(true);
  });

  it("defaults to active version when hasDraft is false", () => {
    const app = makeApp({ hasDraft: false, status: "ACTIVE" });
    const versions = [makeVersion("v1"), makeVersion("v2", { version: "0.9.0" })];
    const { result } = renderHook(() => useAppVersion(), {
      wrapper: wrapper(app, versions),
    });

    expect(result.current.selectedVersion).toBe("v1");
    expect(result.current.isDraft).toBe(false);
    expect(result.current.isActive).toBe(true);
  });

  it("switching to older version makes data not visible", () => {
    const app = makeApp({ hasDraft: true });
    const versions = [makeVersion("v2", { version: "2.0.0" }), makeVersion("v1", { version: "1.0.0" })];
    const { result } = renderHook(() => useAppVersion(), {
      wrapper: wrapper(app, versions),
    });

    act(() => {
      result.current.setSelectedVersion("v1");
    });

    expect(result.current.selectedVersion).toBe("v1");
    expect(result.current.isDataVisible).toBe(false);
    expect(result.current.isReadOnly).toBe(true);
    expect(result.current.isActive).toBe(false);
  });

  it("switching back to draft restores data visibility", () => {
    const app = makeApp({ hasDraft: true });
    const versions = [makeVersion("v2", { version: "2.0.0" }), makeVersion("v1", { version: "1.0.0" })];
    const { result } = renderHook(() => useAppVersion(), {
      wrapper: wrapper(app, versions),
    });

    act(() => {
      result.current.setSelectedVersion("v1");
    });

    expect(result.current.isDataVisible).toBe(false);

    act(() => {
      result.current.setSelectedVersion("draft");
    });

    expect(result.current.selectedVersion).toBe("draft");
    expect(result.current.isDraft).toBe(true);
    expect(result.current.isDataVisible).toBe(true);
  });

  it("defaults to draft for DRAFT-status app with no versions", () => {
    const app = makeApp({ hasDraft: false, status: "DRAFT" });
    const versions: AppVersionResponse[] = [];
    const { result } = renderHook(() => useAppVersion(), {
      wrapper: wrapper(app, versions),
    });

    expect(result.current.selectedVersion).toBe("draft");
    expect(result.current.isDraft).toBe(true);
  });

  it("isReadOnly is true for non-draft selections", () => {
    const app = makeApp({ hasDraft: false, status: "ACTIVE" });
    const versions = [makeVersion("v1")];
    const { result } = renderHook(() => useAppVersion(), {
      wrapper: wrapper(app, versions),
    });

    // active version is not draft → read only
    expect(result.current.isDraft).toBe(false);
    expect(result.current.isReadOnly).toBe(true);
  });
});

describe("useAppVersion", () => {
  it("throws when used outside of AppVersionProvider", () => {
    expect(() =>
      renderHook(() => useAppVersion()),
    ).toThrow("useAppVersion must be used within an AppVersionProvider");
  });
});
