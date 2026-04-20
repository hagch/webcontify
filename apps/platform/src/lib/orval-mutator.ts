/**
 * Orval custom instance — used by generated API hooks.
 * Token management is handled by setClientAccessToken() at runtime.
 * BASE_URL is built dynamically from the current hostname to include the tenant subdomain.
 * e.g. 123123123.localhost → http://api.123123123.localhost
 *      platform.localhost  → http://api.localhost
 */

import { getApiBaseUrl } from "@/lib/auth-utils";

function getBaseUrl(): string {
  if (typeof window === "undefined") return "http://api.localhost";
  return getApiBaseUrl(window.location.hostname);
}

let clientAccessToken: string | undefined;

export function setClientAccessToken(token: string | undefined): void {
  clientAccessToken = token;
}

export const customInstance = async <T>(
  url: string,
  options?: RequestInit
): Promise<T> => {
  const authHeaders: Record<string, string> = clientAccessToken
    ? { Authorization: `Bearer ${clientAccessToken}` }
    : {};

  // Don't set Content-Type for FormData — browser sets it automatically
  // with the correct multipart/form-data boundary
  const isFormData = options?.body instanceof FormData;
  const contentTypeHeaders: Record<string, string> = isFormData
    ? {}
    : { "Content-Type": "application/json" };

  const response = await fetch(`${getBaseUrl()}${url}`, {
    ...options,
    headers: {
      ...contentTypeHeaders,
      ...authHeaders,
      ...options?.headers,
    },
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => null);
    throw {
      data: errorData,
      status: response.status,
      headers: response.headers,
    };
  }

  const data =
    response.status === 204 ? undefined : await response.json();

  return { data, status: response.status, headers: response.headers } as T;
};
