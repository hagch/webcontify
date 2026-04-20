import { describe, it, expect } from "vitest";
import { FONT_CSS_VAR_MAP, FONT_CSS_FALLBACK_MAP } from "../fonts";

describe("fonts", () => {
  it("should map all font keys to CSS variable references", () => {
    expect(FONT_CSS_VAR_MAP["inter"]).toBe("var(--font-inter)");
    expect(FONT_CSS_VAR_MAP["roboto"]).toBe("var(--font-roboto)");
    expect(FONT_CSS_VAR_MAP["merriweather"]).toBe("var(--font-merriweather)");
  });

  it("should map system to system-ui", () => {
    expect(FONT_CSS_VAR_MAP["system"]).toBe("system-ui, sans-serif");
  });

  it("should map geist to its CSS variable", () => {
    expect(FONT_CSS_VAR_MAP["geist"]).toBe("var(--font-geist)");
  });

  it("should have fallback CSS map for non-next/font contexts", () => {
    expect(FONT_CSS_FALLBACK_MAP["inter"]).toBe("'Inter', sans-serif");
    expect(FONT_CSS_FALLBACK_MAP["merriweather"]).toBe("'Merriweather', serif");
    expect(FONT_CSS_FALLBACK_MAP["system"]).toBe("system-ui, sans-serif");
  });

  it("should have entries for all 13 fonts (12 + system)", () => {
    expect(Object.keys(FONT_CSS_VAR_MAP)).toHaveLength(13);
    expect(Object.keys(FONT_CSS_FALLBACK_MAP)).toHaveLength(13);
  });
});
