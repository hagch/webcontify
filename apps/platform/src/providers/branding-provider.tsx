"use client";

import { createContext, useContext, useEffect, useMemo } from "react";
import { useGetTenantSelfConfig, useGetFile } from "@/generated/tenant";
import { hexToOklch } from "@/lib/color-utils";
import { FONT_CSS_VAR_MAP } from "@/lib/fonts";

export interface BrandingValues {
  primaryColor: string | null;
  secondaryColor: string | null;
  accentColor: string | null;
  dangerColor: string | null;
  successColor: string | null;
  warningColor: string | null;
  fontFamily: string | null;
  borderRadius: string | null;
  logoStorageKey: string | null;
  faviconStorageKey: string | null;
  tenantName: string | null;
  tenantSlug: string | null;
  /** Pre-resolved logo URL from server-side fetch */
  resolvedLogoUrl: string | null;
  /** Pre-resolved favicon URL from server-side fetch */
  resolvedFaviconUrl: string | null;
}

const DEFAULTS: BrandingValues = {
  primaryColor: "#6366F1",
  secondaryColor: "#10B981",
  accentColor: "#10B981",
  dangerColor: "#EF4444",
  successColor: "#059669",
  warningColor: "#F59E0B",
  fontFamily: "system",
  borderRadius: "0.5rem",
  logoStorageKey: null,
  faviconStorageKey: null,
  tenantName: null,
  tenantSlug: null,
  resolvedLogoUrl: null,
  resolvedFaviconUrl: null,
};

const BrandingContext = createContext<BrandingValues>(DEFAULTS);

export function useBranding() {
  return useContext(BrandingContext);
}

export interface ServerTenantConfig {
  name?: string;
  slug?: string;
  branding?: {
    primaryColor?: string;
    secondaryColor?: string;
    accentColor?: string;
    dangerColor?: string;
    successColor?: string;
    warningColor?: string;
    fontFamily?: string;
    borderRadius?: string;
    logoStorageKey?: string;
    faviconStorageKey?: string;
  };
  /** Pre-resolved S3 URLs for logo and favicon */
  resolvedLogoUrl?: string;
  resolvedFaviconUrl?: string;
}

interface BrandingProviderProps {
  children: React.ReactNode;
  /** Server-side prefetched config to avoid flash of default branding */
  initialConfig?: ServerTenantConfig | null;
}

/**
 * Applies tenant branding CSS variables to the document root.
 * Only used in workspace layout — platform admin pages use default theme.
 *
 * When `initialConfig` is provided (server-side prefetch), the provider
 * renders immediately with the correct branding. The client-side query
 * still runs to keep data fresh, but the initial render is correct.
 */
export function BrandingProvider({ children, initialConfig }: BrandingProviderProps) {
  const { data, isLoading } = useGetTenantSelfConfig();

  // Use client data if available, otherwise fall back to server-prefetched config
  const source = data?.data ?? initialConfig;
  const branding = source?.branding ?? initialConfig?.branding;

  const values = useMemo<BrandingValues>(
    () => ({
      primaryColor: branding?.primaryColor || DEFAULTS.primaryColor,
      secondaryColor: branding?.secondaryColor || DEFAULTS.secondaryColor,
      accentColor: branding?.accentColor || DEFAULTS.accentColor,
      dangerColor: branding?.dangerColor || DEFAULTS.dangerColor,
      successColor: branding?.successColor || DEFAULTS.successColor,
      warningColor: branding?.warningColor || DEFAULTS.warningColor,
      fontFamily: branding?.fontFamily || DEFAULTS.fontFamily,
      borderRadius: branding?.borderRadius || DEFAULTS.borderRadius,
      logoStorageKey: branding?.logoStorageKey || null,
      faviconStorageKey: branding?.faviconStorageKey || null,
      tenantName: source?.name || null,
      tenantSlug: source?.slug || null,
      resolvedLogoUrl: initialConfig?.resolvedLogoUrl || null,
      resolvedFaviconUrl: initialConfig?.resolvedFaviconUrl || null,
    }),
    [branding, source?.name, source?.slug, initialConfig?.resolvedLogoUrl, initialConfig?.resolvedFaviconUrl]
  );

  // Use server-resolved favicon URL, fall back to client-side resolution
  const faviconKey = branding?.faviconStorageKey;
  const { data: faviconFileData } = useGetFile({ storageKey: faviconKey ?? "" }, { query: { enabled: !!faviconKey && !initialConfig?.resolvedFaviconUrl } });
  const faviconUrl = initialConfig?.resolvedFaviconUrl || (faviconFileData?.status === 200 ? faviconFileData.data.url : undefined);

  useEffect(() => {
    if (!initialConfig && isLoading) return;
    applyBrandingToDocument(values);
    return () => removeBrandingFromDocument();
  }, [values, isLoading, initialConfig]);

  useEffect(() => {
    if (!faviconUrl) return;
    let link = document.querySelector<HTMLLinkElement>("link[rel='icon']");
    if (!link) {
      link = document.createElement("link");
      link.rel = "icon";
      document.head.appendChild(link);
    }
    link.href = faviconUrl;
  }, [faviconUrl]);

  // Only block rendering if we have no server data AND client is still loading
  if (!initialConfig && isLoading) {
    return null;
  }

  return (
    <BrandingContext.Provider value={values}>
      {children}
    </BrandingContext.Provider>
  );
}

/**
 * Applies branding CSS variables to a specific element (for live preview).
 */
export function applyBrandingToElement(
  element: HTMLElement,
  values: Partial<BrandingValues>
) {
  const overrides = buildCssOverrides(values);
  for (const [prop, value] of Object.entries(overrides)) {
    element.style.setProperty(prop, value);
  }
}

function applyBrandingToDocument(values: BrandingValues) {
  const root = document.documentElement;
  const overrides = buildCssOverrides(values);
  for (const [prop, value] of Object.entries(overrides)) {
    root.style.setProperty(prop, value);
  }
}

function removeBrandingFromDocument() {
  const root = document.documentElement;
  const props = [
    "--primary",
    "--ring",
    "--sidebar-primary",
    "--sidebar-accent-foreground",
    "--sidebar-ring",
    "--chart-1",
    "--accent",
    "--chart-2",
    "--radius",
    "--brand-secondary",
    "--destructive",
    "--success",
    "--warning",
    "--font-sans",
  ];
  for (const prop of props) {
    root.style.removeProperty(prop);
  }
}

export function buildCssOverrides(
  values: Partial<BrandingValues>
): Record<string, string> {
  const overrides: Record<string, string> = {};

  if (values.primaryColor) {
    const oklch = hexToOklch(values.primaryColor);
    if (oklch) {
      const oklchValue = `oklch(${oklch})`;
      overrides["--primary"] = oklchValue;
      overrides["--ring"] = oklchValue;
      overrides["--sidebar-primary"] = oklchValue;
      overrides["--sidebar-accent-foreground"] = oklchValue;
      overrides["--sidebar-ring"] = oklchValue;
      overrides["--chart-1"] = oklchValue;
    }
  }

  if (values.accentColor) {
    const oklch = hexToOklch(values.accentColor);
    if (oklch) {
      const oklchValue = `oklch(${oklch})`;
      overrides["--accent"] = oklchValue;
      overrides["--chart-2"] = oklchValue;
    }
  }

  if (values.secondaryColor) {
    overrides["--brand-secondary"] = values.secondaryColor;
  }

  if (values.dangerColor) {
    const oklch = hexToOklch(values.dangerColor);
    if (oklch) {
      overrides["--destructive"] = `oklch(${oklch})`;
    }
  }

  if (values.successColor) {
    const oklch = hexToOklch(values.successColor);
    if (oklch) {
      overrides["--success"] = `oklch(${oklch})`;
    }
  }

  if (values.warningColor) {
    const oklch = hexToOklch(values.warningColor);
    if (oklch) {
      overrides["--warning"] = `oklch(${oklch})`;
    }
  }

  if (values.borderRadius) {
    overrides["--radius"] = values.borderRadius;
  }

  if (values.fontFamily && values.fontFamily !== "system" && values.fontFamily !== "geist") {
    const cssVar = FONT_CSS_VAR_MAP[values.fontFamily];
    if (cssVar) {
      overrides["--font-sans"] = cssVar;
    }
  }

  return overrides;
}

export { DEFAULTS as BRANDING_DEFAULTS };
