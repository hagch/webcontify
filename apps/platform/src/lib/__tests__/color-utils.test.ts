import { describe, it, expect } from "vitest";
import { hexToHsl, hexToOklch } from "@/lib/color-utils";

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

describe("hexToOklch", () => {
  it("converts pure black (#000000)", () => {
    const result = hexToOklch("#000000");
    expect(result).toBe("0 0 0");
  });

  it("converts pure white (#ffffff)", () => {
    const result = hexToOklch("#ffffff");
    expect(result).not.toBeNull();
    const [l] = result!.split(" ").map(Number);
    expect(l).toBeCloseTo(1.0, 1);
  });

  it("converts Indigo 500 (#6366F1) close to expected OKLCH values", () => {
    // globals.css: oklch(0.585 0.233 277)
    const result = hexToOklch("#6366F1");
    expect(result).not.toBeNull();
    const [l, c, h] = result!.split(" ").map(Number);
    expect(l).toBeCloseTo(0.585, 1);
    expect(c).toBeCloseTo(0.233, 1);
    expect(h).toBeCloseTo(277, 5);
  });

  it("converts Emerald 500 (#10B981) to reasonable OKLCH values", () => {
    const result = hexToOklch("#10B981");
    expect(result).not.toBeNull();
    const [l, c, h] = result!.split(" ").map(Number);
    // Emerald 500 should be green-ish (H ~160), medium lightness, moderate chroma
    expect(l).toBeGreaterThan(0.6);
    expect(l).toBeLessThan(0.8);
    expect(c).toBeGreaterThan(0.1);
    expect(c).toBeLessThan(0.25);
    expect(h).toBeCloseTo(163, -1);
  });

  it("returns null for invalid hex", () => {
    expect(hexToOklch("not-a-hex")).toBeNull();
    expect(hexToOklch("#fff")).toBeNull();
    expect(hexToOklch("")).toBeNull();
  });

  it("is case-insensitive", () => {
    expect(hexToOklch("#FF0000")).toBe(hexToOklch("#ff0000"));
  });
});
