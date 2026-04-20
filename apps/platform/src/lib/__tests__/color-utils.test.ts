import { describe, it, expect } from "vitest";
import { hexToHsl } from "@/lib/color-utils";

describe("hexToHsl", () => {
  it("konvertiert reines Rot (#ff0000) korrekt", () => {
    expect(hexToHsl("#ff0000")).toBe("0 100% 50%");
  });

  it("konvertiert reines Gruen (#00ff00) korrekt", () => {
    expect(hexToHsl("#00ff00")).toBe("120 100% 50%");
  });

  it("konvertiert reines Blau (#0000ff) korrekt", () => {
    expect(hexToHsl("#0000ff")).toBe("240 100% 50%");
  });

  it("konvertiert Schwarz (#000000) korrekt", () => {
    expect(hexToHsl("#000000")).toBe("0 0% 0%");
  });

  it("konvertiert Weiss (#ffffff) korrekt", () => {
    expect(hexToHsl("#ffffff")).toBe("0 0% 100%");
  });

  it("konvertiert Teal-Primaerfarbe (#0d9488) korrekt", () => {
    // Ergibt ca. hsl(175, 84%, 32%)
    const result = hexToHsl("#0d9488");
    expect(result).toMatch(/^17[45] \d+% \d+%$/);
  });

  it("konvertiert Standard-Blau (#3b82f6) korrekt", () => {
    // hsl(217, 91%, 60%)
    const result = hexToHsl("#3b82f6");
    expect(result).toMatch(/^217 \d+% \d+%$/);
  });

  it("ist case-insensitiv bei Hex-Eingabe", () => {
    expect(hexToHsl("#FF0000")).toBe(hexToHsl("#ff0000"));
    expect(hexToHsl("#3B82F6")).toBe(hexToHsl("#3b82f6"));
  });

  it("gibt null zurueck bei ungueltigem Hex-Wert (kein #)", () => {
    expect(hexToHsl("ff0000")).toBeNull();
  });

  it("gibt null zurueck bei zu kurzem Hex-Wert", () => {
    expect(hexToHsl("#fff")).toBeNull();
  });

  it("gibt null zurueck bei ungueltigem Hex-Wert (nicht-hex Zeichen)", () => {
    expect(hexToHsl("#xyz123")).toBeNull();
  });

  it("gibt null zurueck bei leerem String", () => {
    expect(hexToHsl("")).toBeNull();
  });
});
