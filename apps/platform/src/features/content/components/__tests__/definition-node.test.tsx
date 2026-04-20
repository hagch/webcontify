import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { DefinitionNode, type DefinitionNodeData } from "../definition-node";
import type {
  ContentDefinitionResponse,
  FieldDefinitionResponse,
} from "@/generated/content";
import type { NodeProps, Node } from "@xyflow/react";

// Mock next-intl
vi.mock("next-intl", () => ({
  useTranslations: (ns: string) => {
    const translator = (key: string, params?: Record<string, unknown>) => {
      let result = `${ns}.${key}`;
      if (params) {
        for (const [k, v] of Object.entries(params)) {
          result = result.replace(`{${k}}`, String(v));
        }
      }
      return result;
    };
    translator.has = () => false;
    return translator;
  },
}));

// Mock @xyflow/react Handle component
vi.mock("@xyflow/react", () => ({
  Handle: ({ id, ...props }: { id?: string; [key: string]: unknown }) => (
    <div data-testid={`handle-${id ?? "unknown"}`} {...props} />
  ),
  Position: { Left: "left", Right: "right", Top: "top", Bottom: "bottom" },
  memo: (fn: unknown) => fn,
}));

function createDefinition(
  overrides?: Partial<ContentDefinitionResponse>
): ContentDefinitionResponse {
  return {
    id: "def-1",
    appId: "app-1",
    name: "Articles",
    slug: "articles",
    fields: [],
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function createField(
  overrides?: Partial<FieldDefinitionResponse>
): FieldDefinitionResponse {
  return {
    id: "field-1",
    fieldKey: "title",
    fieldType: "TEXT",
    required: false,
    unique: false,
    sortable: false,
    filterable: false,
    position: 0,
    ...overrides,
  };
}

function renderNode(data: DefinitionNodeData) {
  // DefinitionNode is a memo'd component that expects NodeProps
  const props = {
    id: data.definition.id,
    data,
    type: "definition",
    selected: false,
    isConnectable: true,
    zIndex: 0,
    positionAbsoluteX: 0,
    positionAbsoluteY: 0,
    dragging: false,
    deletable: false,
    selectable: false,
    parentId: undefined,
    sourcePosition: undefined,
    targetPosition: undefined,
    width: 280,
    height: 100,
    dragHandle: undefined,
  } as unknown as NodeProps<Node<DefinitionNodeData>>;

  return render(<DefinitionNode {...props} />);
}

describe("DefinitionNode", () => {
  it("renders the definition name", () => {
    renderNode({ definition: createDefinition({ name: "Products" }) });
    expect(screen.getByText("Products")).toBeInTheDocument();
  });

  it("renders field list", () => {
    const fields = [
      createField({ id: "f1", fieldKey: "title", position: 0 }),
      createField({ id: "f2", fieldKey: "slug", position: 1 }),
      createField({
        id: "f3",
        fieldKey: "published",
        fieldType: "BOOLEAN",
        position: 2,
      }),
    ];
    renderNode({ definition: createDefinition({ fields }) });

    expect(screen.getByText("title")).toBeInTheDocument();
    expect(screen.getByText("slug")).toBeInTheDocument();
    expect(screen.getByText("published")).toBeInTheDocument();
  });

  it("shows '+N more' for more than 5 fields", () => {
    const fields = Array.from({ length: 7 }, (_, i) =>
      createField({ id: `f${i}`, fieldKey: `field_${i}`, position: i })
    );
    renderNode({ definition: createDefinition({ fields }) });

    // Should show first 5 fields
    expect(screen.getByText("field_0")).toBeInTheDocument();
    expect(screen.getByText("field_4")).toBeInTheDocument();
    // Should NOT show field 5 and 6
    expect(screen.queryByText("field_5")).not.toBeInTheDocument();
    // Should show "+2 more"
    expect(screen.getByText("content.graph.moreFields")).toBeInTheDocument();
  });

  it("calls onEditDefinition on header double-click", () => {
    const onEditDefinition = vi.fn();
    renderNode({
      definition: createDefinition({ id: "def-99" }),
      onEditDefinition,
    });

    // Double-click on the header area (the div containing the name)
    const header = screen.getByText("Articles").closest("div[class*='border-b']");
    expect(header).toBeTruthy();
    fireEvent.doubleClick(header!);
    expect(onEditDefinition).toHaveBeenCalledWith("def-99");
  });

  it("calls onAddField on add field button click", () => {
    const onAddField = vi.fn();
    renderNode({
      definition: createDefinition({ id: "def-42" }),
      onAddField,
    });

    const addButton = screen.getByTestId("add-field-def-42");
    fireEvent.click(addButton);
    expect(onAddField).toHaveBeenCalledWith("def-42");
  });

  it("shows no fields message when definition has no fields", () => {
    renderNode({ definition: createDefinition({ fields: [] }) });
    expect(screen.getByText("content.graph.noFields")).toBeInTheDocument();
  });

  it("does not render add field button when onAddField is not provided", () => {
    renderNode({ definition: createDefinition({ id: "def-1" }) });
    expect(screen.queryByTestId("add-field-def-1")).not.toBeInTheDocument();
  });
});
