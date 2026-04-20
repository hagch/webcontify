const AUTH_BASE_URL =
  process.env.KEYCLOAK_BASE_URL ?? "http://auth.localhost";
const PLATFORM_HOSTNAME = "platform.localhost";
const PLATFORM_REALM = "webcontify-platform";

export function isPlatformHost(hostname: string): boolean {
  return hostname === PLATFORM_HOSTNAME || hostname === "localhost";
}

export function getRealmFromHostname(hostname: string): string {
  if (isPlatformHost(hostname)) return PLATFORM_REALM;
  const slug = hostname.split(".")[0];
  return `tenant-${slug}`;
}

export function getKeycloakIssuer(hostname: string): string {
  const realm = getRealmFromHostname(hostname);
  return `${AUTH_BASE_URL}/realms/${realm}`;
}

export function getApiBaseUrl(hostname: string): string {
  if (process.env.NEXT_PUBLIC_API_URL) return process.env.NEXT_PUBLIC_API_URL;
  if (isPlatformHost(hostname)) return "http://api.localhost";
  const slug = hostname.split(".")[0];
  return `http://api.${slug}.localhost`;
}
