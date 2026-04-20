import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { FieldCellRenderer, buildTableColumns } from "../entry-table-columns";
import type { FieldDefinitionResponse } from "@/generated/content";

// Mock next/link
vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

function makeField(
  overrides: Partial<FieldDefinitionResponse> & {
    fieldKey: string;
    fieldType: FieldDefinitionResponse["fieldType"];
  }
): FieldDefinitionResponse {
  return {
    id: "00000000-0000-0000-0000-000000000001",
    required: true,
    unique: false,
    sortable: false,
    filterable: false,
    position: 0,
    ...overrides,
  };
}

describe("FieldCellRenderer", () => {
  it("renders BOOLEAN true as badge with 'Yes'", () => {
    const field = makeField({ fieldKey: "active", fieldType: "BOOLEAN" });
    render(
      <FieldCellRenderer
        field={field}
        value={true}
        appSlug="my-app"
        defSlug="posts"
      />
    );
    expect(screen.getByTestId("cell-active-badge")).toHaveTextContent("Yes");
  });

  it("renders BOOLEAN false as badge with 'No'", () => {
    const field = makeField({ fieldKey: "active", fieldType: "BOOLEAN" });
    render(
      <FieldCellRenderer
        field={field}
        value={false}
        appSlug="my-app"
        defSlug="posts"
      />
    );
    expect(screen.getByTestId("cell-active-badge")).toHaveTextContent("No");
  });

  it("renders DATE as formatted string", () => {
    const field = makeField({ fieldKey: "born", fieldType: "DATE" });
    render(
      <FieldCellRenderer
        field={field}
        value="2026-01-15"
        appSlug="my-app"
        defSlug="posts"
      />
    );
    const cell = screen.getByTestId("cell-born");
    expect(cell.textContent).toContain("2026");
  });

  it("renders ENUM as outline badge", () => {
    const field = makeField({
      fieldKey: "status",
      fieldType: "ENUM",
      config: {
        fieldType: "ENUM",
        allowedValues: ["DRAFT", "ACTIVE"],
      },
    });
    render(
      <FieldCellRenderer
        field={field}
        value="DRAFT"
        appSlug="my-app"
        defSlug="posts"
      />
    );
    expect(screen.getByTestId("cell-status-badge")).toHaveTextContent("DRAFT");
  });

  it("renders REFERENCE with display as link", () => {
    const field = makeField({
      fieldKey: "author",
      fieldType: "REFERENCE",
      config: {
        fieldType: "REFERENCE",
        targetDefinitionId: "00000000-0000-0000-0000-000000000002",
        relationType: "MANY_TO_ONE",
        onDelete: "SET_NULL",
      },
    });
    render(
      <FieldCellRenderer
        field={field}
        value={{ id: "550e8400-e29b-41d4-a716-446655440000", display: "John Doe" }}
        appSlug="my-app"
        defSlug="posts"
      />
    );
    const link = screen.getByTestId("cell-author-link");
    expect(link).toHaveTextContent("John Doe");
    expect(link).toHaveAttribute(
      "href",
      "/apps/my-app/data/posts/550e8400-e29b-41d4-a716-446655440000"
    );
  });

  it("renders TEXT as truncated string", () => {
    const field = makeField({ fieldKey: "title", fieldType: "TEXT" });
    render(
      <FieldCellRenderer
        field={field}
        value="Hello World"
        appSlug="my-app"
        defSlug="posts"
      />
    );
    expect(screen.getByTestId("cell-title")).toHaveTextContent("Hello World");
  });

  it("renders null as dash", () => {
    const field = makeField({ fieldKey: "title", fieldType: "TEXT" });
    render(
      <FieldCellRenderer
        field={field}
        value={null}
        appSlug="my-app"
        defSlug="posts"
      />
    );
    expect(screen.getByText("-")).toBeInTheDocument();
  });

  it("renders MIRROR array as count badge", () => {
    const field = makeField({ fieldKey: "refs", fieldType: "MIRROR" });
    render(
      <FieldCellRenderer
        field={field}
        value={[{ id: "1", display: "A" }, { id: "2", display: "B" }]}
        appSlug="my-app"
        defSlug="posts"
      />
    );
    expect(screen.getByTestId("cell-refs-badge")).toHaveTextContent("2");
  });
});

describe("buildTableColumns", () => {
  it("builds columns sorted by position", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({ fieldKey: "b", fieldType: "TEXT", position: 2 }),
      makeField({ fieldKey: "a", fieldType: "TEXT", position: 1 }),
    ];
    const columns = buildTableColumns(fields);
    expect(columns[0].label).toBe("a");
    expect(columns[1].label).toBe("b");
  });

  it("sets sortable/filterable flags from field definitions", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({
        fieldKey: "title",
        fieldType: "TEXT",
        sortable: true,
        filterable: true,
      }),
    ];
    const columns = buildTableColumns(fields);
    expect(columns[0].sortable).toBe(true);
    expect(columns[0].filterable).toBe(true);
  });
});
