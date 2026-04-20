import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { EntryFormPage, EntryFormActions } from "../entry-form-page";
import type { ContentDefinitionResponse, ContentEntryResponse } from "@/generated/content";

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("@/generated/content", () => ({
  useCreateContentEntry: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateContentEntry: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteContentEntry: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useGetContentEntryHistory: () => ({ data: undefined, isLoading: false }),
  getListContentEntriesQueryKey: vi.fn(),
  getGetContentEntryQueryKey: vi.fn(),
}));

function makeDefinition(overrides?: Partial<ContentDefinitionResponse>): ContentDefinitionResponse {
  return {
    id: "def-1",
    slug: "products",
    name: "Products",
    fields: [
      { id: "f1", fieldKey: "name", fieldType: "TEXT", required: true, unique: false, sortable: true, filterable: true, position: 0 },
      { id: "f2", fieldKey: "price", fieldType: "DECIMAL", required: true, unique: false, sortable: true, filterable: false, position: 1 },
      { id: "f3", fieldKey: "active", fieldType: "BOOLEAN", required: false, unique: false, sortable: false, filterable: false, position: 2 },
    ],
    ...overrides,
  } as ContentDefinitionResponse;
}

function makeEntry(): ContentEntryResponse {
  return {
    id: "entry-1",
    version: 1,
    values: { cf_name: "Widget", cf_price: 9.99, cf_active: true },
    createdAt: "2026-04-15T14:23:00Z",
    updatedAt: "2026-04-17T09:15:00Z",
    createdBy: "user-1",
    updatedBy: "user-1",
  } as ContentEntryResponse;
}

describe("EntryFormPage", () => {
  it("renders form fields from definition in a responsive grid", () => {
    const def = makeDefinition();
    render(<EntryFormPage definition={def} appSlug="shop" allDefinitions={[def]} />);
    expect(screen.getByTestId("entry-form-grid")).toHaveClass("grid-cols-1");
    expect(screen.getByTestId("entry-form-grid")).toHaveClass("lg:grid-cols-3");
  });

  it("renders all non-MIRROR fields as form inputs", () => {
    const def = makeDefinition();
    render(<EntryFormPage definition={def} appSlug="shop" allDefinitions={[def]} />);
    expect(screen.getByText("name")).toBeInTheDocument();
    expect(screen.getByText("price")).toBeInTheDocument();
    expect(screen.getByText("active")).toBeInTheDocument();
  });

  it("renders system fields when entry is provided", () => {
    const def = makeDefinition();
    const entry = makeEntry();
    render(<EntryFormPage definition={def} appSlug="shop" entry={entry} allDefinitions={[def]} />);
    expect(screen.getByTestId("system-fields")).toBeInTheDocument();
    expect(screen.getByText("detail.fields.id")).toBeInTheDocument();
    expect(screen.getByText("detail.fields.version")).toBeInTheDocument();
  });

  it("does not render system fields in create mode", () => {
    const def = makeDefinition();
    render(<EntryFormPage definition={def} appSlug="shop" allDefinitions={[def]} />);
    expect(screen.queryByTestId("system-fields")).not.toBeInTheDocument();
  });

});

describe("EntryFormActions", () => {
  it("renders save button in create mode", () => {
    render(
      <EntryFormActions
        isEdit={false}
        isPending={false}
        onHistoryOpen={vi.fn()}
        onDeleteOpen={vi.fn()}
      />
    );
    expect(screen.getByTestId("entry-save-button")).toBeInTheDocument();
    expect(screen.queryByTestId("entry-delete-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("entry-history-button")).not.toBeInTheDocument();
  });

  it("renders save, history, and delete buttons in edit mode", () => {
    render(
      <EntryFormActions
        isEdit={true}
        isPending={false}
        onHistoryOpen={vi.fn()}
        onDeleteOpen={vi.fn()}
      />
    );
    expect(screen.getByTestId("entry-save-button")).toBeInTheDocument();
    expect(screen.getByTestId("entry-delete-button")).toBeInTheDocument();
    expect(screen.getByTestId("entry-history-button")).toBeInTheDocument();
  });

  it("disables save button when pending", () => {
    render(
      <EntryFormActions
        isEdit={false}
        isPending={true}
        onHistoryOpen={vi.fn()}
        onDeleteOpen={vi.fn()}
      />
    );
    expect(screen.getByTestId("entry-save-button")).toBeDisabled();
  });

  it("calls onHistoryOpen when history button clicked", () => {
    const onHistoryOpen = vi.fn();
    render(
      <EntryFormActions
        isEdit={true}
        isPending={false}
        onHistoryOpen={onHistoryOpen}
        onDeleteOpen={vi.fn()}
      />
    );
    screen.getByTestId("entry-history-button").click();
    expect(onHistoryOpen).toHaveBeenCalledOnce();
  });

  it("calls onDeleteOpen when delete button clicked", () => {
    const onDeleteOpen = vi.fn();
    render(
      <EntryFormActions
        isEdit={true}
        isPending={false}
        onHistoryOpen={vi.fn()}
        onDeleteOpen={onDeleteOpen}
      />
    );
    screen.getByTestId("entry-delete-button").click();
    expect(onDeleteOpen).toHaveBeenCalledOnce();
  });
});
