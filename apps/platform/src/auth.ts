import NextAuth from "next-auth";
import Keycloak from "next-auth/providers/keycloak";
import { getKeycloakIssuer } from "@/lib/auth-utils";

// Platform Admins behalten ihre Platform-Session auch auf Tenant-Subdomains.
// Das Backend akzeptiert PLATFORM_ADMIN Tokens auf allen Tenants (RLS Bypass).
// Vollstaendiges dynamisches Issuer-Switching pro Request wird in Phase 3b
// implementiert, wenn Tenant-User sich einloggen koennen.
const KEYCLOAK_ISSUER =
  process.env.KEYCLOAK_ISSUER ?? getKeycloakIssuer("platform.localhost");

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [
    Keycloak({
      clientId: process.env.AUTH_KEYCLOAK_CLIENT_ID ?? "platform-ui",
      issuer: KEYCLOAK_ISSUER,
      client: {
        token_endpoint_auth_method: "none",
      },
      authorization: {
        params: {
          scope: "openid profile email offline_access",
        },
      },
      checks: ["pkce", "state"],
    }),
  ],
  pages: {
    signIn: "/signin",
  },
  session: {
    strategy: "jwt",
  },
  callbacks: {
    async jwt({ token, account }) {
      // Beim ersten Login: Tokens vom Provider speichern
      if (account) {
        token.accessToken = account.access_token;
        token.refreshToken = account.refresh_token;
        token.expiresAt = account.expires_at;
        token.idToken = account.id_token;
      }

      // Kein Refresh Token vorhanden: Token unveraendert zurueckgeben
      if (!token.refreshToken) {
        return token;
      }

      // Token noch gultig (60 Sekunden Puffer)
      const expiresAt = token.expiresAt as number | undefined;
      if (expiresAt && Date.now() < expiresAt * 1000 - 60_000) {
        return token;
      }

      // Access Token abgelaufen: Refresh
      return refreshAccessToken(token);
    },
    async session({ session, token }) {
      session.accessToken = token.accessToken as string | undefined;
      session.error = token.error as string | undefined;
      return session;
    },
  },
});

interface JwtToken {
  accessToken?: string;
  refreshToken?: string;
  expiresAt?: number;
  idToken?: string;
  error?: string;
  [key: string]: unknown;
}

async function refreshAccessToken(token: JwtToken): Promise<JwtToken> {
  try {
    const url = `${KEYCLOAK_ISSUER}/protocol/openid-connect/token`;
    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        client_id: process.env.AUTH_KEYCLOAK_CLIENT_ID ?? "platform-ui",
        refresh_token: token.refreshToken ?? "",
      }),
    });

    if (!response.ok) {
      throw new Error(`Token refresh failed: ${response.status}`);
    }

    const refreshed = await response.json();

    return {
      ...token,
      accessToken: refreshed.access_token,
      refreshToken: refreshed.refresh_token ?? token.refreshToken,
      expiresAt: Math.floor(Date.now() / 1000) + refreshed.expires_in,
      idToken: refreshed.id_token ?? token.idToken,
      error: undefined,
    };
  } catch (error) {
    console.error("Failed to refresh access token", error);
    return {
      ...token,
      error: "RefreshAccessTokenError",
    };
  }
}
