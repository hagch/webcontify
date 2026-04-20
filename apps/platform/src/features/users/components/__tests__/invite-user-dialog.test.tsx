import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { InviteUserDialog } from "../invite-user-dialog";

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

import { useInviteUser } from "@/generated/identity";

function renderDialog() {
  return render(
    <InviteUserDialog>
      <button>Einladen</button>
    </InviteUserDialog>
  );
}

describe("InviteUserDialog", () => {
  beforeEach(() => {
    vi.mocked(useInviteUser).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useInviteUser>);
  });

  it("should render form fields when dialog is opened", async () => {
    renderDialog();

    // Open dialog by clicking trigger
    const trigger = screen.getByRole("button", { name: "Einladen" });
    fireEvent.click(trigger);

    await waitFor(() => {
      // Form labels are rendered
      expect(screen.getByText(/e-mail/i)).toBeInTheDocument();
      expect(screen.getByText(/vorname/i)).toBeInTheDocument();
      expect(screen.getByText(/nachname/i)).toBeInTheDocument();
      // Inputs are accessible via placeholder
      expect(screen.getByPlaceholderText("max@beispiel.de")).toBeInTheDocument();
      expect(screen.getByPlaceholderText("Max")).toBeInTheDocument();
      expect(screen.getByPlaceholderText("Mustermann")).toBeInTheDocument();
    });
  });

  it("should show validation errors for empty fields on submit", async () => {
    renderDialog();

    // Open dialog
    const trigger = screen.getByRole("button", { name: "Einladen" });
    fireEvent.click(trigger);

    // Wait for dialog content
    await waitFor(() => {
      expect(screen.getByText("Benutzer einladen")).toBeInTheDocument();
    });

    // Submit without filling fields — find all buttons named "Einladen"
    const submitButtons = screen.getAllByRole("button", { name: /einladen/i });
    // The last one is the submit button in the dialog footer
    const dialogSubmit = submitButtons[submitButtons.length - 1];
    fireEvent.click(dialogSubmit);

    await waitFor(() => {
      // Zod validation: firstName and lastName required, email invalid
      expect(screen.getByText("Vorname ist erforderlich")).toBeInTheDocument();
      expect(screen.getByText("Nachname ist erforderlich")).toBeInTheDocument();
    });
  });
});
