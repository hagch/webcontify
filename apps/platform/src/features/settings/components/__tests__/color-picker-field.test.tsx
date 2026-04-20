import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { useForm } from "react-hook-form";
import { Form } from "@/components/ui/form";
import { ColorPickerField } from "../color-picker-field";

function TestWrapper({ defaultValue = "" }: { defaultValue?: string }) {
  const form = useForm({
    defaultValues: { primaryColor: defaultValue },
  });
  return (
    <Form {...form}>
      <form>
        <ColorPickerField
          name="primaryColor"
          label="Primärfarbe"
          control={form.control}
        />
      </form>
    </Form>
  );
}

describe("ColorPickerField", () => {
  it("rendert Label, Hex-Input und Color-Picker", () => {
    render(<TestWrapper />);

    expect(screen.getByText("Primärfarbe")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("#000000")).toBeInTheDocument();
    expect(screen.getByLabelText("Primärfarbe Farbwähler")).toBeInTheDocument();
  });

  it("aktualisiert Hex-Input wenn Color-Picker ändert", () => {
    render(<TestWrapper defaultValue="#ffffff" />);

    const colorInput = screen.getByLabelText("Primärfarbe Farbwähler");
    fireEvent.change(colorInput, { target: { value: "#ff0000" } });

    const hexInput = screen.getByPlaceholderText("#000000") as HTMLInputElement;
    expect(hexInput.value).toBe("#ff0000");
  });

  it("aktualisiert Color-Picker wenn Hex-Input ändert", () => {
    render(<TestWrapper defaultValue="#ffffff" />);

    const hexInput = screen.getByPlaceholderText("#000000");
    fireEvent.change(hexInput, { target: { value: "#0000ff" } });

    const colorInput = screen.getByLabelText("Primärfarbe Farbwähler") as HTMLInputElement;
    expect(colorInput.value).toBe("#0000ff");
  });
});
