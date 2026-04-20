const PROTECTED_PREFIXES = [
  // Platform Admin
  "/tenants",
  "/admins",
  // Workspace
  "/dashboard",
  "/apps",
  "/users",
  "/roles",
  "/settings",
  // Shared
  "/profile",
];

const PUBLIC_PREFIXES = ["/api/auth", "/signin", "/_next", "/favicon"];

/**
 * Gibt true zuruck wenn der Pfad unter Route Protection steht.
 */
export function isProtectedPath(pathname: string): boolean {
  return PROTECTED_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

/**
 * Gibt true zuruck wenn der Pfad oeffentlich zugaenglich ist
 * (NextAuth Callbacks, statische Assets).
 */
export function isPublicPath(pathname: string): boolean {
  return PUBLIC_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}
