import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InviteUserDialog } from "../invite-user-dialog";

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

import { useInviteUser, useListUsers, useGetUserRoles } from "@/generated/identity";

const mockUseInviteUser = vi.mocked(useInviteUser);
const mockUseListUsers = vi.mocked(useListUsers);
const mockUseGetUserRoles = vi.mocked(useGetUserRoles);

const sampleUsers = {
  data: {
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
  },
  isPending: false,
  isLoading: false,
};

const sampleRoles = {
  data: {
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
  },
  isPending: false,
  isLoading: false,
};

function renderDialog(props?: { initialCopyFromUserId?: string }) {
  return render(
    <InviteUserDialog {...props}>
      <button>Einladen</button>
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
      sampleUsers as unknown as ReturnType<typeof useListUsers>
    );

    mockUseGetUserRoles.mockReturnValue(
      { data: undefined, isPending: false, isLoading: false } as unknown as ReturnType<typeof useGetUserRoles>
    );
  });

  it("should render form fields when dialog is opened", async () => {
    renderDialog();

    const trigger = screen.getByRole("button", { name: "Einladen" });
    fireEvent.click(trigger);

    await waitFor(() => {
      expect(screen.getByText(/e-mail/i)).toBeInTheDocument();
      expect(screen.getByText(/vorname/i)).toBeInTheDocument();
      expect(screen.getByText(/nachname/i)).toBeInTheDocument();
      expect(screen.getByPlaceholderText("max@beispiel.de")).toBeInTheDocument();
      expect(screen.getByPlaceholderText("Max")).toBeInTheDocument();
      expect(screen.getByPlaceholderText("Mustermann")).toBeInTheDocument();
    });
  });

  it("should show validation errors for empty fields on submit", async () => {
    renderDialog();

    const trigger = screen.getByRole("button", { name: "Einladen" });
    fireEvent.click(trigger);

    await waitFor(() => {
      expect(screen.getByText("Benutzer einladen")).toBeInTheDocument();
    });

    const submitButtons = screen.getAllByRole("button", { name: /einladen/i });
    const dialogSubmit = submitButtons[submitButtons.length - 1];
    fireEvent.click(dialogSubmit);

    await waitFor(() => {
      expect(screen.getByText("Vorname ist erforderlich")).toBeInTheDocument();
      expect(screen.getByText("Nachname ist erforderlich")).toBeInTheDocument();
    });
  });

  it("should render 'Berechtigungen uebernehmen von' select field", async () => {
    renderDialog();

    const trigger = screen.getByRole("button", { name: "Einladen" });
    fireEvent.click(trigger);

    await waitFor(() => {
      expect(
        screen.getByText("Berechtigungen uebernehmen von")
      ).toBeInTheDocument();
    });
  });

  it("should open dialog and pre-fill copy-from user when initialCopyFromUserId is set", async () => {
    renderDialog({ initialCopyFromUserId: "user-1" });

    await waitFor(() => {
      expect(screen.getByText("Benutzer einladen")).toBeInTheDocument();
    });
  });

  it("should show role badges when a user is selected in the copy-from field", async () => {
    mockUseGetUserRoles.mockReturnValue(
      sampleRoles as unknown as ReturnType<typeof useGetUserRoles>
    );

    renderDialog({ initialCopyFromUserId: "user-1" });

    await waitFor(() => {
      expect(screen.getByText("Admin")).toBeInTheDocument();
      expect(screen.getByText("Editor")).toBeInTheDocument();
    });
  });
});
