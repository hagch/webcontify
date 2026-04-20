import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { UserTable } from "../user-table";
import type { UserResponse } from "@/generated/identity";
import { UserStatus } from "@/generated/identity";

// Mock use-user-actions
vi.mock("../../hooks/use-user-actions", () => ({
  useUserActions: vi.fn(),
}));

// Mock @/generated/identity
vi.mock("@/generated/identity", () => ({
  useInviteUser: vi.fn(),
  useListUsers: vi.fn(),
  useGetUserRoles: vi.fn(),
  getListUsersQueryKey: vi.fn().mockReturnValue(["users"]),
  UserStatus: {
    INVITED: "INVITED",
    ACTIVE: "ACTIVE",
    DEACTIVATED: "DEACTIVATED",
  },
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
  default: ({ href, children, className }: { href: string; children: React.ReactNode; className?: string }) => (
    <a href={href} className={className}>{children}</a>
  ),
}));

import { useUserActions } from "../../hooks/use-user-actions";
import { useListUsers, useGetUserRoles } from "@/generated/identity";

const mockUseUserActions = vi.mocked(useUserActions);
const mockUseListUsers = vi.mocked(useListUsers);
const mockUseGetUserRoles = vi.mocked(useGetUserRoles);

function defaultUserActions() {
  return {
    resendInvite: vi.fn(),
    activateUser: vi.fn(),
    deactivateUser: vi.fn(),
    deleteUser: vi.fn(),
    isResendingInvite: false,
    isUpdatingStatus: false,
    isDeletingUser: false,
  };
}

const sampleUsers: UserResponse[] = [
  {
    id: "user-1",
    email: "max@beispiel.de",
    firstName: "Max",
    lastName: "Mustermann",
    status: "ACTIVE" as UserStatus,
    createdAt: "2026-03-15T10:00:00Z",
    updatedAt: "2026-03-15T10:00:00Z",
  },
  {
    id: "user-2",
    email: "erika@beispiel.de",
    firstName: "Erika",
    lastName: "Musterfrau",
    status: "INVITED" as UserStatus,
    createdAt: "2026-03-14T08:00:00Z",
    updatedAt: "2026-03-14T08:00:00Z",
  },
];

describe("UserTable", () => {
  beforeEach(() => {
    mockUseUserActions.mockReturnValue(defaultUserActions());

    mockUseListUsers.mockReturnValue({
      data: { status: 200, data: { items: [] } },
      isPending: false,
      isLoading: false,
    } as unknown as ReturnType<typeof useListUsers>);

    mockUseGetUserRoles.mockReturnValue({
      data: undefined,
      isPending: false,
      isLoading: false,
    } as unknown as ReturnType<typeof useGetUserRoles>);
  });

  it("should show empty state when no users", () => {
    render(<UserTable users={[]} isLoading={false} isRefetching={false} />);

    expect(screen.getByText("Noch keine Benutzer")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Lade Benutzer ein, um mit der Zusammenarbeit zu beginnen."
      )
    ).toBeInTheDocument();
  });

  it("should render user data correctly", () => {
    render(
      <UserTable users={sampleUsers} isLoading={false} isRefetching={false} />
    );

    expect(screen.getByText("Max Mustermann")).toBeInTheDocument();
    expect(screen.getByText("Erika Musterfrau")).toBeInTheDocument();
    expect(screen.getByText("max@beispiel.de")).toBeInTheDocument();
    expect(screen.getByText("erika@beispiel.de")).toBeInTheDocument();
    expect(screen.getByText("Aktiv")).toBeInTheDocument();
    expect(screen.getByText("Eingeladen")).toBeInTheDocument();
  });

  it("should show skeleton when loading", () => {
    render(<UserTable users={[]} isLoading={true} isRefetching={false} />);

    expect(screen.queryByText("Noch keine Benutzer")).not.toBeInTheDocument();
    const skeletonRows = document.querySelectorAll(".animate-pulse");
    expect(skeletonRows.length).toBeGreaterThan(0);
  });

  it("should show 'Mit gleichen Rollen einladen' action only for ACTIVE users", async () => {
    render(
      <UserTable users={sampleUsers} isLoading={false} isRefetching={false} />
    );

    // Open actions menu for Max Mustermann (ACTIVE)
    const actionButtons = screen.getAllByRole("button", { name: /aktionen fuer/i });
    fireEvent.click(actionButtons[0]); // Max Mustermann — ACTIVE

    await waitFor(() => {
      expect(
        screen.getByText("Mit gleichen Rollen einladen")
      ).toBeInTheDocument();
    });
  });

  it("should NOT show 'Mit gleichen Rollen einladen' for INVITED users", async () => {
    render(
      <UserTable users={sampleUsers} isLoading={false} isRefetching={false} />
    );

    // Open actions menu for Erika Musterfrau (INVITED)
    const actionButtons = screen.getAllByRole("button", { name: /aktionen fuer/i });
    fireEvent.click(actionButtons[1]); // Erika Musterfrau — INVITED

    await waitFor(() => {
      expect(
        screen.queryByText("Mit gleichen Rollen einladen")
      ).not.toBeInTheDocument();
    });
  });
});
