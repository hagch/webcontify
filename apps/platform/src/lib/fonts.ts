import {
  Inter,
  Roboto,
  Lato,
  Poppins,
  Merriweather,
  DM_Sans,
  Plus_Jakarta_Sans,
  Source_Sans_3,
  IBM_Plex_Sans,
  Open_Sans,
  Nunito_Sans,
  Geist,
} from "next/font/google";

// Each font with its own CSS variable
export const fontGeist = Geist({ variable: "--font-geist", subsets: ["latin"] });
export const fontInter = Inter({ variable: "--font-inter", subsets: ["latin"] });
export const fontDmSans = DM_Sans({ variable: "--font-dm-sans", subsets: ["latin"] });
export const fontPlusJakarta = Plus_Jakarta_Sans({ variable: "--font-plus-jakarta-sans", subsets: ["latin"] });
export const fontRoboto = Roboto({ variable: "--font-roboto", subsets: ["latin"], weight: ["400", "500", "600", "700"] });
export const fontSourceSans = Source_Sans_3({ variable: "--font-source-sans-3", subsets: ["latin"] });
export const fontIbmPlex = IBM_Plex_Sans({ variable: "--font-ibm-plex-sans", subsets: ["latin"], weight: ["400", "500", "600", "700"] });
export const fontOpenSans = Open_Sans({ variable: "--font-open-sans", subsets: ["latin"] });
export const fontLato = Lato({ variable: "--font-lato", subsets: ["latin"], weight: ["400", "700"] });
export const fontNunitoSans = Nunito_Sans({ variable: "--font-nunito-sans", subsets: ["latin"] });
export const fontPoppins = Poppins({ variable: "--font-poppins", subsets: ["latin"], weight: ["400", "500", "600", "700"] });
export const fontMerriweather = Merriweather({ variable: "--font-merriweather", subsets: ["latin"], weight: ["400", "700"] });

// All font instances for className injection in layout
export const allFontVariables = [
  fontGeist, fontInter, fontDmSans, fontPlusJakarta, fontRoboto,
  fontSourceSans, fontIbmPlex, fontOpenSans, fontLato,
  fontNunitoSans, fontPoppins, fontMerriweather,
];

// Mapping: font key (from API) → CSS var() reference (for BrandingProvider)
export const FONT_CSS_VAR_MAP: Record<string, string> = {
  system: "system-ui, sans-serif",
  geist: "var(--font-geist)",
  inter: "var(--font-inter)",
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
};

// Fallback map for contexts without next/font (e.g., Keycloak theme)
export const FONT_CSS_FALLBACK_MAP: Record<string, string> = {
  system: "system-ui, sans-serif",
  geist: "'Geist', sans-serif",
  inter: "'Inter', sans-serif",
  "dm-sans": "'DM Sans', sans-serif",
  "plus-jakarta-sans": "'Plus Jakarta Sans', sans-serif",
  roboto: "'Roboto', sans-serif",
  "source-sans-3": "'Source Sans 3', sans-serif",
  "ibm-plex-sans": "'IBM Plex Sans', sans-serif",
  "open-sans": "'Open Sans', sans-serif",
  lato: "'Lato', sans-serif",
  "nunito-sans": "'Nunito Sans', sans-serif",
  poppins: "'Poppins', sans-serif",
  merriweather: "'Merriweather', serif",
};
