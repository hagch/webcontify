import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FontSelect } from "../font-select";

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => {
    const map: Record<string, string> = {
      "fontFamilyOptions.system": "System Default",
      "fontFamilyOptions.inter": "Inter",
      "fontFamilyOptions.geist": "Geist",
      "fontFamilyOptions.dm-sans": "DM Sans",
      "fontFamilyOptions.plus-jakarta-sans": "Plus Jakarta Sans",
      "fontFamilyOptions.roboto": "Roboto",
      "fontFamilyOptions.source-sans-3": "Source Sans 3",
      "fontFamilyOptions.ibm-plex-sans": "IBM Plex Sans",
      "fontFamilyOptions.open-sans": "Open Sans",
      "fontFamilyOptions.lato": "Lato",
      "fontFamilyOptions.nunito-sans": "Nunito Sans",
      "fontFamilyOptions.poppins": "Poppins",
      "fontFamilyOptions.merriweather": "Merriweather",
      fontCategoryModern: "Modern",
      fontCategoryClassic: "Classic",
      fontCategoryFriendly: "Friendly",
      fontCategorySerif: "Serif",
    };
    return map[key] ?? key;
  },
}));

vi.mock("@/lib/fonts", () => ({
  FONT_CSS_VAR_MAP: {
    system: "system-ui, sans-serif",
    inter: "var(--font-inter)",
    geist: "var(--font-geist)",
    "dm-sans": "var(--font-dm-sans)",
    "plus-jakarta-sans": "var(--font-plus-jakarta-sans)",
    roboto: "var(--font-roboto)",
    "source-sans-3": "var(--font-source-sans-3)",
    "ibm-plex-sans": "var(--font-ibm-plex-sans)",
    "open-sans": "var(--font-open-sans)",
    lato: "var(--font-lato)",
    "nunito-sans": "var(--font-nunito-sans)",
    poppins: "var(--font-poppins)",
    merriweather: "var(--font-merriweather)",
  },
}));

describe("FontSelect", () => {
  it("renders with the current value displayed", () => {
    render(<FontSelect value="inter" onChange={vi.fn()} />);
    expect(screen.getByRole("combobox")).toBeInTheDocument();
  });

  it("opens the dropdown and shows all category labels", async () => {
    const user = userEvent.setup();
    render(<FontSelect value="system" onChange={vi.fn()} />);

    await user.click(screen.getByRole("combobox"));

    expect(screen.getByText("Modern")).toBeInTheDocument();
    expect(screen.getByText("Classic")).toBeInTheDocument();
    expect(screen.getByText("Friendly")).toBeInTheDocument();
    expect(screen.getByText("Serif")).toBeInTheDocument();
  });

  it("shows system default as first option without a category", async () => {
    const user = userEvent.setup();
    render(<FontSelect value="system" onChange={vi.fn()} />);

    await user.click(screen.getByRole("combobox"));

    expect(screen.getAllByText("System Default").length).toBeGreaterThanOrEqual(1);
  });

  it("shows all font options in the dropdown", async () => {
    const user = userEvent.setup();
    render(<FontSelect value="system" onChange={vi.fn()} />);

    await user.click(screen.getByRole("combobox"));

    expect(screen.getByText("Inter")).toBeInTheDocument();
    expect(screen.getByText("Geist")).toBeInTheDocument();
    expect(screen.getByText("DM Sans")).toBeInTheDocument();
    expect(screen.getByText("Plus Jakarta Sans")).toBeInTheDocument();
    expect(screen.getByText("Roboto")).toBeInTheDocument();
    expect(screen.getByText("Source Sans 3")).toBeInTheDocument();
    expect(screen.getByText("IBM Plex Sans")).toBeInTheDocument();
    expect(screen.getByText("Open Sans")).toBeInTheDocument();
    expect(screen.getByText("Lato")).toBeInTheDocument();
    expect(screen.getByText("Nunito Sans")).toBeInTheDocument();
    expect(screen.getByText("Poppins")).toBeInTheDocument();
    expect(screen.getByText("Merriweather")).toBeInTheDocument();
  });

  it("calls onChange when a font is selected", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<FontSelect value="system" onChange={onChange} />);

    await user.click(screen.getByRole("combobox"));
    await user.click(screen.getByText("Inter"));

    expect(onChange).toHaveBeenCalledWith("inter");
  });

  it("is disabled when the disabled prop is set", () => {
    render(<FontSelect value="inter" onChange={vi.fn()} disabled />);

    expect(screen.getByRole("combobox")).toBeDisabled();
  });
});
