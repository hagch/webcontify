import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { AdminTable } from "../admin-table";
import type { PlatformAdminResponse } from "@/generated/platform-admin";

// Mock next-intl
vi.mock("next-intl", () => ({
  useTranslations: (ns: string) => {
    const translator = (key: string) => `${ns}.${key}`;
    translator.has = () => false;
    return translator;
  },
}));

// Mock use-admin-actions
vi.mock("../../hooks/use-admin-actions", () => ({
  useAdminActions: vi.fn(),
}));

// Mock AdminActionsMenu
vi.mock("../admin-actions-menu", () => ({
  AdminActionsMenu: vi.fn(() => null),
}));

// Mock @/generated/platform-admin
vi.mock("@/generated/platform-admin", () => ({
  useDeletePlatformAdmin: vi.fn(),
  useUpdatePlatformAdminStatus: vi.fn(),
  useResetPlatformAdminPassword: vi.fn(),
  getListPlatformAdminsQueryKey: vi.fn(() => ["admins"]),
}));

// Mock @tanstack/react-query
vi.mock("@tanstack/react-query", () => ({
  useQueryClient: vi.fn(() => ({ invalidateQueries: vi.fn() })),
}));

// Mock sonner
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import { useAdminActions } from "../../hooks/use-admin-actions";

const mockUseAdminActions = vi.mocked(useAdminActions);

function defaultAdminActions() {
  return {
    deleteAdmin: vi.fn(),
    activateAdmin: vi.fn(),
    deactivateAdmin: vi.fn(),
    resetPassword: vi.fn(),
    isDeletingAdmin: false,
    isUpdatingStatus: false,
    isResettingPassword: false,
  };
}

const sampleAdmins: PlatformAdminResponse[] = [
  {
    id: "admin-1",
    email: "max@beispiel.de",
    firstName: "Max",
    lastName: "Mustermann",
    enabled: true,
    createdAt: "2026-03-01T00:00:00Z",
  },
  {
    id: "admin-2",
    email: "erika@beispiel.de",
    firstName: "Erika",
    lastName: "Musterfrau",
    enabled: false,
    createdAt: "2026-03-02T00:00:00Z",
  },
];

describe("AdminTable", () => {
  beforeEach(() => {
    mockUseAdminActions.mockReturnValue(defaultAdminActions());
  });

  it("should render admin names", () => {
    render(<AdminTable admins={sampleAdmins} isLoading={false} isRefetching={false} />);

    expect(screen.getByText("Max Mustermann")).toBeInTheDocument();
    expect(screen.getByText("Erika Musterfrau")).toBeInTheDocument();
  });

  it("should show active badge for enabled admins", () => {
    render(<AdminTable admins={sampleAdmins} isLoading={false} isRefetching={false} />);

    expect(screen.getByText("admins.statusValues.active")).toBeInTheDocument();
  });

  it("should show deactivated badge for disabled admins", () => {
    render(<AdminTable admins={sampleAdmins} isLoading={false} isRefetching={false} />);

    expect(screen.getByText("admins.statusValues.deactivated")).toBeInTheDocument();
  });

  it("should show empty state when no admins", () => {
    render(<AdminTable admins={[]} isLoading={false} isRefetching={false} />);

    expect(screen.getByText("admins.emptyState.title")).toBeInTheDocument();
    expect(screen.getByText("admins.emptyState.description")).toBeInTheDocument();
  });

  it("should show skeleton when loading", () => {
    render(<AdminTable admins={[]} isLoading={true} isRefetching={false} />);

    expect(screen.queryByText("admins.emptyState.title")).not.toBeInTheDocument();
    const skeletonElements = document.querySelectorAll(".animate-pulse");
    expect(skeletonElements.length).toBeGreaterThan(0);
  });
});
