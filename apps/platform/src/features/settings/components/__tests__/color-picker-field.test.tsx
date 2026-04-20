import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { useForm } from "react-hook-form";
import { Form } from "@/components/ui/form";
import { ColorPickerField } from "../color-picker-field";

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string, params?: Record<string, string>) => {
    if (key === "colorPickerLabel" && params?.label) {
      return `${params.label} color picker`;
    }
    return key;
  },
}));

function TestWrapper({ defaultValue = "" }: { defaultValue?: string }) {
  const form = useForm({
    defaultValues: { primaryColor: defaultValue },
  });
  return (
    <Form {...form}>
      <form>
        <ColorPickerField
          name="primaryColor"
          label="Primary Color"
          control={form.control}
        />
      </form>
    </Form>
  );
}

describe("ColorPickerField", () => {
  it("renders label, hex input and color picker trigger", () => {
    render(<TestWrapper />);

    expect(screen.getByText("Primary Color")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("#000000")).toBeInTheDocument();
    expect(screen.getByLabelText("Primary Color color picker")).toBeInTheDocument();
  });

  it("shows default value in hex input", () => {
    render(<TestWrapper defaultValue="#ff0000" />);

    const hexInput = screen.getByPlaceholderText("#000000") as HTMLInputElement;
    expect(hexInput.value).toBe("#ff0000");
  });

  it("updates form value when hex input changes", () => {
    render(<TestWrapper defaultValue="#ffffff" />);

    const hexInput = screen.getByPlaceholderText("#000000") as HTMLInputElement;
    fireEvent.change(hexInput, { target: { value: "#0000ff" } });

    expect(hexInput.value).toBe("#0000ff");
  });

  it("shows color picker trigger with background color", () => {
    render(<TestWrapper defaultValue="#ff0000" />);

    const trigger = screen.getByLabelText("Primary Color color picker");
    expect(trigger).toHaveStyle({ backgroundColor: "#ff0000" });
  });
});
