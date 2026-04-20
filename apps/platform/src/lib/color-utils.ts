/**
 * Converts a hex color to OKLCH format for use in CSS variables.
 * The globals.css uses OKLCH color space, so dynamic overrides must match.
 *
 * @param hex - Hex color with leading #, e.g. "#6366F1"
 * @returns OKLCH string in format "L C H" (e.g. "0.585 0.233 277") or null for invalid input
 */
export function hexToOklch(hex: string): string | null {
  const match = /^#([0-9a-fA-F]{6})$/.exec(hex);
  if (!match) {
    return null;
  }

  // sRGB → linear RGB
  const srgbToLinear = (c: number) =>
    c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);

  const r = srgbToLinear(parseInt(match[1].slice(0, 2), 16) / 255);
  const g = srgbToLinear(parseInt(match[1].slice(2, 4), 16) / 255);
  const b = srgbToLinear(parseInt(match[1].slice(4, 6), 16) / 255);

  // Linear sRGB → LMS (via Oklab's M1 matrix, combines sRGB→XYZ→LMS)
  const l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
  const m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
  const s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;

  // Cube root (non-linearity)
  const l_ = Math.cbrt(l);
  const m_ = Math.cbrt(m);
  const s_ = Math.cbrt(s);

  // LMS cube-root → OKLab (M2 matrix)
  const L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_;
  const a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_;
  const bLab = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_;

  // OKLab → OKLCH
  const C = Math.sqrt(a * a + bLab * bLab);
  let H = (Math.atan2(bLab, a) * 180) / Math.PI;
  if (H < 0) H += 360;

  // Round to 3 decimal places
  const lRound = Math.round(L * 1000) / 1000;
  const cRound = Math.round(C * 1000) / 1000;
  const hRound = Math.round(H);

  return `${lRound} ${cRound} ${hRound}`;
}

/**
 * Konvertiert einen Hex-Farbwert in HSL-Format ohne hsl()-Wrapper.
 * Tailwind v4 CSS Variables erwarten das Format "217 91% 60%" (nicht "hsl(217, 91%, 60%)").
 *
 * @param hex - Hex-Farbwert mit fuehrendem #, z.B. "#3b82f6"
 * @returns HSL-String im Format "H S% L%" oder null bei ungueltigem Input
 */
export function hexToHsl(hex: string): string | null {
  const match = /^#([0-9a-fA-F]{6})$/.exec(hex);
  if (!match) {
    return null;
  }

  const r = parseInt(match[1].slice(0, 2), 16) / 255;
  const g = parseInt(match[1].slice(2, 4), 16) / 255;
  const b = parseInt(match[1].slice(4, 6), 16) / 255;

  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const delta = max - min;

  let h = 0;
  let s = 0;
  const l = (max + min) / 2;

  if (delta !== 0) {
    s = delta / (1 - Math.abs(2 * l - 1));

    if (max === r) {
      h = ((g - b) / delta + (g < b ? 6 : 0)) / 6;
    } else if (max === g) {
      h = ((b - r) / delta + 2) / 6;
    } else {
      h = ((r - g) / delta + 4) / 6;
    }
  }

  const hDeg = Math.round(h * 360);
  const sPct = Math.round(s * 100);
  const lPct = Math.round(l * 100);

  return `${hDeg} ${sPct}% ${lPct}%`;
}

/**
 * Converts HSL components to a hex color string.
 *
 * @param h - Hue in degrees [0, 360]
 * @param s - Saturation as fraction [0, 1]
 * @param l - Lightness as fraction [0, 1]
 * @returns Hex color string with leading #, e.g. "#6366f1"
 */
export function hslToHex(h: number, s: number, l: number): string {
  const a = s * Math.min(l, 1 - l);
  const toChannel = (n: number) => {
    const k = (n + h / 30) % 12;
    const value = l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1));
    return Math.round(255 * value)
      .toString(16)
      .padStart(2, "0");
  };
  return `#${toChannel(0)}${toChannel(8)}${toChannel(4)}`;
}
