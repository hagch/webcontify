import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
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

const mockUseUserActions = vi.mocked(useUserActions);

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

    // Names rendered as links
    expect(screen.getByText("Max Mustermann")).toBeInTheDocument();
    expect(screen.getByText("Erika Musterfrau")).toBeInTheDocument();

    // Emails
    expect(screen.getByText("max@beispiel.de")).toBeInTheDocument();
    expect(screen.getByText("erika@beispiel.de")).toBeInTheDocument();

    // Status badges
    expect(screen.getByText("Aktiv")).toBeInTheDocument();
    expect(screen.getByText("Eingeladen")).toBeInTheDocument();
  });

  it("should show skeleton when loading", () => {
    render(<UserTable users={[]} isLoading={true} isRefetching={false} />);

    // When loading, UserTableSkeleton renders instead of the table
    // The skeleton component renders animated placeholder rows
    expect(screen.queryByText("Noch keine Benutzer")).not.toBeInTheDocument();
    // UserTableSkeleton renders a table with skeleton rows
    const skeletonRows = document.querySelectorAll(".animate-pulse");
    expect(skeletonRows.length).toBeGreaterThan(0);
  });
});
