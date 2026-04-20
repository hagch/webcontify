// Mock for next/font/google — used in Vitest environment where Next.js build is unavailable.
// Returns a minimal font object with the `variable` property so fonts.ts can be imported.

type FontOptions = {
  variable?: string;
  subsets?: string[];
  weight?: string[];
};

type FontResult = {
  variable: string;
  className: string;
  style: { fontFamily: string };
};

function createFontMock(defaultVariable: string) {
  return (options: FontOptions = {}): FontResult => ({
    variable: options.variable ?? defaultVariable,
    className: options.variable?.replace("--", "") ?? defaultVariable,
    style: { fontFamily: options.variable ?? defaultVariable },
  });
}

export const Geist = createFontMock("--font-geist");
export const Inter = createFontMock("--font-inter");
export const Roboto = createFontMock("--font-roboto");
export const Lato = createFontMock("--font-lato");
export const Poppins = createFontMock("--font-poppins");
export const Merriweather = createFontMock("--font-merriweather");
export const DM_Sans = createFontMock("--font-dm-sans");
export const Plus_Jakarta_Sans = createFontMock("--font-plus-jakarta-sans");
export const Source_Sans_3 = createFontMock("--font-source-sans-3");
export const IBM_Plex_Sans = createFontMock("--font-ibm-plex-sans");
export const Open_Sans = createFontMock("--font-open-sans");
export const Nunito_Sans = createFontMock("--font-nunito-sans");
export const Geist_Mono = createFontMock("--font-mono");
