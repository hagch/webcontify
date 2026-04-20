import { describe, it, expect } from "vitest";
import { buildCssOverrides } from "../branding-provider";

describe("buildCssOverrides", () => {
  it("should set --font-sans for inter", () => {
    const result = buildCssOverrides({ fontFamily: "inter" });
    expect(result["--font-sans"]).toBe("var(--font-inter)");
  });

  it("should NOT set --font-sans for system", () => {
    const result = buildCssOverrides({ fontFamily: "system" });
    expect(result["--font-sans"]).toBeUndefined();
  });

  it("should NOT set --font-sans for geist (default)", () => {
    const result = buildCssOverrides({ fontFamily: "geist" });
    expect(result["--font-sans"]).toBeUndefined();
  });

  it("should set --font-sans for merriweather", () => {
    const result = buildCssOverrides({ fontFamily: "merriweather" });
    expect(result["--font-sans"]).toBe("var(--font-merriweather)");
  });

  it("should set --font-sans for roboto", () => {
    const result = buildCssOverrides({ fontFamily: "roboto" });
    expect(result["--font-sans"]).toBe("var(--font-roboto)");
  });

  it("should still set color overrides", () => {
    const result = buildCssOverrides({ primaryColor: "#FF0000" });
    expect(result["--primary"]).toBeDefined();
    expect(result["--ring"]).toBeDefined();
  });
});
