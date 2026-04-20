import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { RoleTable } from "../role-table";
import type { RoleResponse } from "@/generated/identity";

// Mock use-role-actions
vi.mock("../../hooks/use-role-actions", () => ({
  useRoleActions: vi.fn(),
}));

// Mock @/generated/identity
vi.mock("@/generated/identity", () => ({
  useDeleteRole: vi.fn(),
  getListRolesQueryKey: vi.fn().mockReturnValue(["roles"]),
}));

// Mock @tanstack/react-query
vi.mock("@tanstack/react-query", () => ({
  useQueryClient: vi.fn().mockReturnValue({
    invalidateQueries: vi.fn(),
  }),
}));

// Mock sonner
vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

// Mock next/link
vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    className,
  }: {
    href: string;
    children: React.ReactNode;
    className?: string;
  }) => (
    <a href={href} className={className}>
      {children}
    </a>
  ),
}));

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: vi.fn().mockReturnValue({
    push: vi.fn(),
  }),
}));

import { useRoleActions } from "../../hooks/use-role-actions";

const mockUseRoleActions = vi.mocked(useRoleActions);

function defaultRoleActions() {
  return {
    createRoleMutation: { mutateAsync: vi.fn(), isPending: false },
    updateRoleMutation: { mutateAsync: vi.fn(), isPending: false },
    deleteRole: vi.fn(),
    setRoleAssignees: vi.fn(),
    isCreatingRole: false,
    isUpdatingRole: false,
    isDeletingRole: false,
    isSettingAssignees: false,
  };
}

const sampleRoles: RoleResponse[] = [
  {
    id: "role-1",
    name: "Admin",
    description: "Administrator mit vollen Rechten",
    systemRole: true,
    assigneeCount: 2,
    createdAt: "2026-03-01T00:00:00Z",
    updatedAt: "2026-03-01T00:00:00Z",
  },
  {
    id: "role-2",
    name: "Redakteur",
    description: "Kann Inhalte bearbeiten",
    systemRole: false,
    assigneeCount: 5,
    createdAt: "2026-03-02T00:00:00Z",
    updatedAt: "2026-03-02T00:00:00Z",
  },
];

describe("RoleTable", () => {
  beforeEach(() => {
    mockUseRoleActions.mockReturnValue(
      defaultRoleActions() as unknown as ReturnType<typeof useRoleActions>
    );
  });

  it("should render role names", () => {
    render(<RoleTable roles={sampleRoles} isLoading={false} isRefetching={false} />);

    expect(screen.getByText("Admin")).toBeInTheDocument();
    expect(screen.getByText("Redakteur")).toBeInTheDocument();
  });

  it("should show system role badge for system roles", () => {
    render(<RoleTable roles={sampleRoles} isLoading={false} isRefetching={false} />);

    expect(screen.getByText("System")).toBeInTheDocument();
    expect(screen.getByText("Benutzerdefiniert")).toBeInTheDocument();
  });

  it("should show empty state when no roles", () => {
    render(<RoleTable roles={[]} isLoading={false} isRefetching={false} />);

    expect(screen.getByText("Noch keine Rollen erstellt")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Erstelle eine Rolle, um Benutzerberechtigungen zu verwalten."
      )
    ).toBeInTheDocument();
  });

  it("should show skeleton when loading", () => {
    render(<RoleTable roles={[]} isLoading={true} isRefetching={false} />);

    expect(
      screen.queryByText("Noch keine Rollen erstellt")
    ).not.toBeInTheDocument();
    const skeletonElements = document.querySelectorAll(".animate-pulse");
    expect(skeletonElements.length).toBeGreaterThan(0);
  });

  it("should not show delete action for system roles", async () => {
    render(<RoleTable roles={sampleRoles} isLoading={false} isRefetching={false} />);

    // Fuer die Custom-Rolle (Redakteur) gibt es einen Aktionen-Button
    const actionButtons = screen.getAllByRole("button", { name: /aktionen fuer/i });
    expect(actionButtons).toHaveLength(1);
    expect(actionButtons[0]).toHaveAttribute("aria-label", "Aktionen fuer Redakteur");

    // Den Aktionen-Button oeffnen
    fireEvent.click(actionButtons[0]);

    await waitFor(() => {
      expect(screen.getByText("Loeschen")).toBeInTheDocument();
    });

    // Fuer die System-Rolle (Admin) gibt es keinen Aktionen-Button
    expect(
      screen.queryByRole("button", { name: /aktionen fuer admin/i })
    ).not.toBeInTheDocument();
  });
});
