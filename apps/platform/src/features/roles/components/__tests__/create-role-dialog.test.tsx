import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateRoleDialog } from "../create-role-dialog";

// Mock @/generated/identity
vi.mock("@/generated/identity", () => ({
  useCreateRole: vi.fn(),
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

import { useCreateRole } from "@/generated/identity";

const mockUseCreateRole = vi.mocked(useCreateRole);

function renderDialog() {
  return render(
    <CreateRoleDialog>
      <button>Rolle erstellen</button>
    </CreateRoleDialog>
  );
}

describe("CreateRoleDialog", () => {
  beforeEach(() => {
    mockUseCreateRole.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useCreateRole>);
  });

  it("should render form fields (name and description)", async () => {
    renderDialog();

    const trigger = screen.getByRole("button", { name: "Rolle erstellen" });
    fireEvent.click(trigger);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Rolle erstellen" })).toBeInTheDocument();
      expect(screen.getByText(/^Name/)).toBeInTheDocument();
      expect(screen.getByText("Beschreibung")).toBeInTheDocument();
      expect(screen.getByPlaceholderText("z.B. Redakteur")).toBeInTheDocument();
      expect(
        screen.getByPlaceholderText("Optionale Beschreibung der Rolle")
      ).toBeInTheDocument();
    });
  });

  it("should validate required name field", async () => {
    const user = userEvent.setup();
    renderDialog();

    const trigger = screen.getByRole("button", { name: "Rolle erstellen" });
    await user.click(trigger);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Rolle erstellen" })).toBeInTheDocument();
    });

    // Submit ohne Name
    const submitButton = screen.getByRole("button", { name: "Erstellen" });
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText("Name ist erforderlich")).toBeInTheDocument();
    });
  });

  it("should call createRole mutation on submit", async () => {
    const user = userEvent.setup();
    const mockMutateAsync = vi.fn().mockResolvedValue({ status: 201, data: { id: "role-new" } });

    mockUseCreateRole.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    } as unknown as ReturnType<typeof useCreateRole>);

    renderDialog();

    const trigger = screen.getByRole("button", { name: "Rolle erstellen" });
    await user.click(trigger);

    await waitFor(() => {
      expect(screen.getByPlaceholderText("z.B. Redakteur")).toBeInTheDocument();
    });

    const nameInput = screen.getByPlaceholderText("z.B. Redakteur");
    await user.type(nameInput, "Redakteur");

    const submitButton = screen.getByRole("button", { name: "Erstellen" });
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith({
        data: {
          name: "Redakteur",
          description: undefined,
        },
      });
    });
  });

  it("should show error on duplicate name (409 response)", async () => {
    const user = userEvent.setup();
    const mockMutateAsync = vi.fn().mockRejectedValue({
      status: 409,
      title: "Conflict",
    });

    mockUseCreateRole.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
    } as unknown as ReturnType<typeof useCreateRole>);

    renderDialog();

    const trigger = screen.getByRole("button", { name: "Rolle erstellen" });
    await user.click(trigger);

    await waitFor(() => {
      expect(screen.getByPlaceholderText("z.B. Redakteur")).toBeInTheDocument();
    });

    const nameInput = screen.getByPlaceholderText("z.B. Redakteur");
    await user.type(nameInput, "Admin");

    const submitButton = screen.getByRole("button", { name: "Erstellen" });
    await user.click(submitButton);

    await waitFor(() => {
      expect(
        screen.getByText("Eine Rolle mit diesem Namen existiert bereits.")
      ).toBeInTheDocument();
    });
  });
});
