import { auth } from "@/auth";
import type { ServerTenantConfig } from "@/providers/branding-provider";
import { headers } from "next/headers";
import { getApiBaseUrl } from "@/lib/auth-utils";

async function resolveFileUrl(storageKey: string, accessToken: string, apiUrl: string): Promise<string | undefined> {
  try {
    const params = new URLSearchParams({ storageKey });
    const response = await fetch(`${apiUrl}/api/v1/tenant/files/resolve?${params}`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      cache: "no-store",
    });

    if (!response.ok) return undefined;

    const data = await response.json();
    return data?.url;
  } catch {
    return undefined;
  }
}

/**
 * Fetch tenant config server-side using the session token.
 * Also resolves logo/favicon S3 URLs to avoid client-side flash.
 * Returns null if the fetch fails (e.g. unauthenticated).
 */
export async function fetchTenantConfig(): Promise<ServerTenantConfig | null> {
  try {
    const session = await auth();
    if (!session?.accessToken) return null;

    const headersList = await headers();
    const host = headersList.get("host")?.split(":")[0] ?? "platform.localhost";
    const apiUrl = getApiBaseUrl(host);
    const response = await fetch(`${apiUrl}/api/v1/tenant/config`, {
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
        "Content-Type": "application/json",
      },
      cache: "no-store",
    });

    if (!response.ok) return null;

    const config: ServerTenantConfig = await response.json();

    // Resolve logo and favicon URLs in parallel
    const [resolvedLogoUrl, resolvedFaviconUrl] = await Promise.all([
      config.branding?.logoStorageKey
        ? resolveFileUrl(config.branding.logoStorageKey, session.accessToken, apiUrl)
        : undefined,
      config.branding?.faviconStorageKey
        ? resolveFileUrl(config.branding.faviconStorageKey, session.accessToken, apiUrl)
        : undefined,
    ]);

    return {
      ...config,
      resolvedLogoUrl,
      resolvedFaviconUrl,
    };
  } catch {
    return null;
  }
}
