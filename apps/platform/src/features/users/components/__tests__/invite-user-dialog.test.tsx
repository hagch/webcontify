import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { InviteUserDialog } from "../invite-user-dialog";

// Mock next-intl
vi.mock("next-intl", () => ({
  useTranslations: (ns: string) => {
    const translator = (key: string) => `${ns}.${key}`;
    translator.has = () => false;
    return translator;
  },
}));

// Mock @/generated/identity
vi.mock("@/generated/identity", () => ({
  useInviteUser: vi.fn(),
  useListUsers: vi.fn(),
  useGetUserRoles: vi.fn(),
  useListRoles: vi.fn(),
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

import { useInviteUser, useListUsers, useGetUserRoles, useListRoles } from "@/generated/identity";

const mockUseInviteUser = vi.mocked(useInviteUser);
const mockUseListUsers = vi.mocked(useListUsers);
const mockUseGetUserRoles = vi.mocked(useGetUserRoles);
const mockUseListRoles = vi.mocked(useListRoles);

const sampleUsers = {
  status: 200,
  data: {
    items: [
      {
        id: "user-1",
        email: "max@beispiel.de",
        firstName: "Max",
        lastName: "Mustermann",
        status: "ACTIVE",
        createdAt: "2026-03-15T10:00:00Z",
        updatedAt: "2026-03-15T10:00:00Z",
      },
      {
        id: "user-2",
        email: "erika@beispiel.de",
        firstName: "Erika",
        lastName: "Musterfrau",
        status: "ACTIVE",
        createdAt: "2026-03-14T08:00:00Z",
        updatedAt: "2026-03-14T08:00:00Z",
      },
    ],
  },
};

const sampleRoles = {
  status: 200,
  data: {
    roles: [
      {
        id: "role-1",
        name: "Admin",
        description: "Administrator",
        systemRole: true,
        assigneeCount: 2,
        createdAt: "2026-03-01T00:00:00Z",
        updatedAt: "2026-03-01T00:00:00Z",
      },
      {
        id: "role-2",
        name: "Editor",
        description: null,
        systemRole: false,
        assigneeCount: 5,
        createdAt: "2026-03-01T00:00:00Z",
        updatedAt: "2026-03-01T00:00:00Z",
      },
    ],
  },
};

function renderDialog(props?: { initialCopyFromUserId?: string }) {
  return render(
    <InviteUserDialog {...props}>
      <button>Open</button>
    </InviteUserDialog>
  );
}

describe("InviteUserDialog", () => {
  beforeEach(() => {
    mockUseInviteUser.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useInviteUser>);

    mockUseListUsers.mockReturnValue(
      { data: sampleUsers, isPending: false, isLoading: false } as unknown as ReturnType<typeof useListUsers>
    );

    mockUseGetUserRoles.mockReturnValue(
      { data: undefined, isPending: false, isLoading: false } as unknown as ReturnType<typeof useGetUserRoles>
    );

    mockUseListRoles.mockReturnValue(
      { data: sampleRoles, isPending: false, isLoading: false } as unknown as ReturnType<typeof useListRoles>
    );
  });

  it("should render form fields when dialog is opened", async () => {
    renderDialog();

    const trigger = screen.getByRole("button", { name: "Open" });
    fireEvent.click(trigger);

    await waitFor(() => {
      expect(screen.getByText(/users\.inviteDialog\.emailLabel/)).toBeInTheDocument();
      expect(screen.getByText(/users\.inviteDialog\.firstNameLabel/)).toBeInTheDocument();
      expect(screen.getByText(/users\.inviteDialog\.lastNameLabel/)).toBeInTheDocument();
      expect(screen.getByPlaceholderText("users.inviteDialog.emailPlaceholder")).toBeInTheDocument();
      expect(screen.getByPlaceholderText("users.inviteDialog.firstNamePlaceholder")).toBeInTheDocument();
      expect(screen.getByPlaceholderText("users.inviteDialog.lastNamePlaceholder")).toBeInTheDocument();
    });
  });

  it("should render the roles section with checkboxes", async () => {
    renderDialog();

    const trigger = screen.getByRole("button", { name: "Open" });
    fireEvent.click(trigger);

    await waitFor(() => {
      expect(screen.getByText("users.inviteDialog.rolesLabel")).toBeInTheDocument();
      expect(screen.getByTestId("role-checkbox-role-1")).toBeInTheDocument();
      expect(screen.getByTestId("role-checkbox-role-2")).toBeInTheDocument();
      expect(screen.getByText("Admin")).toBeInTheDocument();
      expect(screen.getByText("Editor")).toBeInTheDocument();
    });
  });

  it("should show empty state when no roles are available", async () => {
    mockUseListRoles.mockReturnValue(
      { data: { status: 200, data: { roles: [] } }, isPending: false, isLoading: false } as unknown as ReturnType<typeof useListRoles>
    );

    renderDialog();

    const trigger = screen.getByRole("button", { name: "Open" });
    fireEvent.click(trigger);

    await waitFor(() => {
      expect(screen.getByText("users.inviteDialog.rolesEmptyState")).toBeInTheDocument();
    });
  });

  it("should toggle role selection when checkbox is clicked", async () => {
    renderDialog();

    const trigger = screen.getByRole("button", { name: "Open" });
    fireEvent.click(trigger);

    await waitFor(() => {
      expect(screen.getByTestId("role-checkbox-role-1")).toBeInTheDocument();
    });

    const adminCheckbox = screen.getByTestId("role-checkbox-role-1");
    expect(adminCheckbox).not.toBeChecked();

    fireEvent.click(adminCheckbox);

    await waitFor(() => {
      expect(adminCheckbox).toBeChecked();
    });
  });

  it("should render copy-roles select field", async () => {
    renderDialog();

    const trigger = screen.getByRole("button", { name: "Open" });
    fireEvent.click(trigger);

    await waitFor(() => {
      expect(
        screen.getByText("users.inviteDialog.copyRolesLabel")
      ).toBeInTheDocument();
    });
  });

  it("should open dialog automatically when initialCopyFromUserId is set", async () => {
    renderDialog({ initialCopyFromUserId: "user-1" });

    await waitFor(() => {
      expect(screen.getByText("users.inviteDialog.title")).toBeInTheDocument();
    });
  });

  it("should pre-fill role checkboxes when copy-from user roles are loaded", async () => {
    mockUseGetUserRoles.mockReturnValue(
      { data: sampleRoles, isPending: false, isLoading: false } as unknown as ReturnType<typeof useGetUserRoles>
    );

    renderDialog({ initialCopyFromUserId: "user-1" });

    await waitFor(() => {
      const adminCheckbox = screen.getByTestId("role-checkbox-role-1");
      const editorCheckbox = screen.getByTestId("role-checkbox-role-2");
      expect(adminCheckbox).toBeChecked();
      expect(editorCheckbox).toBeChecked();
    });
  });
});
