"use client";

import { useSession } from "next-auth/react";

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = atob(parts[1]);
    return JSON.parse(payload);
  } catch {
    return null;
  }
}

export function useAuth() {
  const { data: session, status } = useSession();

  const isLoading = status === "loading";
  const isAuthenticated = status === "authenticated";
  const user = session?.user ?? null;

  let isPlatformAdmin = false;
  if (session?.accessToken) {
    const payload = decodeJwtPayload(session.accessToken);
    const roles =
      (payload?.realm_access as { roles?: string[] })?.roles ?? [];
    isPlatformAdmin = roles.includes("PLATFORM_ADMIN");
  }

  return {
    user,
    isAuthenticated,
    isLoading,
    isPlatformAdmin,
  };
}
