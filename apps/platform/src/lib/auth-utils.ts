const AUTH_BASE_URL =
  process.env.KEYCLOAK_BASE_URL ?? "http://auth.localhost";
const PLATFORM_HOSTNAME = "platform.localhost";
const PLATFORM_REALM = "webcontify-platform";

export function isPlatformHost(hostname: string): boolean {
  return hostname === PLATFORM_HOSTNAME;
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

/**
 * Bestimmt ob die Platform-Session verwendet werden soll.
 * Platform Admins behalten ihre Platform-Session auch auf Tenant-Subdomains.
 * Das Backend akzeptiert PLATFORM_ADMIN Tokens auf allen Tenants.
 */
export function shouldUsePlatformSession(
  isPlatform: boolean,
  isPlatformAdmin: boolean
): boolean {
  return isPlatform || isPlatformAdmin;
}
