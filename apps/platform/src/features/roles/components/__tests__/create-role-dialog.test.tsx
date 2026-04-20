import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateRoleDialog } from "../create-role-dialog";

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
      <button>Open</button>
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

    const trigger = screen.getByRole("button", { name: "Open" });
    fireEvent.click(trigger);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "roles.createDialog.title" })).toBeInTheDocument();
      expect(screen.getByText(/roles\.createDialog\.nameLabel/)).toBeInTheDocument();
      expect(screen.getByText("roles.createDialog.descriptionLabel")).toBeInTheDocument();
      expect(screen.getByPlaceholderText("roles.createDialog.namePlaceholder")).toBeInTheDocument();
      expect(
        screen.getByPlaceholderText("roles.createDialog.descriptionPlaceholder")
      ).toBeInTheDocument();
    });
  });

  it("should validate required name field", async () => {
    const user = userEvent.setup();
    renderDialog();

    const trigger = screen.getByRole("button", { name: "Open" });
    await user.click(trigger);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "roles.createDialog.title" })).toBeInTheDocument();
    });

    // Submit without name
    const submitButton = screen.getByRole("button", { name: "roles.createDialog.submit" });
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

    const trigger = screen.getByRole("button", { name: "Open" });
    await user.click(trigger);

    await waitFor(() => {
      expect(screen.getByPlaceholderText("roles.createDialog.namePlaceholder")).toBeInTheDocument();
    });

    const nameInput = screen.getByPlaceholderText("roles.createDialog.namePlaceholder");
    await user.type(nameInput, "Redakteur");

    const submitButton = screen.getByRole("button", { name: "roles.createDialog.submit" });
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

    const trigger = screen.getByRole("button", { name: "Open" });
    await user.click(trigger);

    await waitFor(() => {
      expect(screen.getByPlaceholderText("roles.createDialog.namePlaceholder")).toBeInTheDocument();
    });

    const nameInput = screen.getByPlaceholderText("roles.createDialog.namePlaceholder");
    await user.type(nameInput, "Admin");

    const submitButton = screen.getByRole("button", { name: "roles.createDialog.submit" });
    await user.click(submitButton);

    await waitFor(() => {
      expect(
        screen.getByText("errors.role.name_already_exists")
      ).toBeInTheDocument();
    });
  });
});
