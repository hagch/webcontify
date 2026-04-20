import NextAuth from "next-auth";
import type { JWT } from "next-auth/jwt";
import Keycloak from "next-auth/providers/keycloak";
import { headers } from "next/headers";
import { getKeycloakIssuer, isPlatformHost } from "@/lib/auth-utils";

export const { handlers, signIn, signOut, auth } = NextAuth(async () => {
  const headersList = await headers();
  const host = headersList.get("host")?.split(":")[0] ?? "platform.localhost";
  const isPlatform = isPlatformHost(host);
  const issuer = getKeycloakIssuer(host);
  const clientId = isPlatform ? "platform-ui" : "tenant-ui";

  return {
    providers: [
      Keycloak({
        clientId,
        issuer,
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
    events: {
      async signOut(message) {
        // End Keycloak session server-side (best-effort)
        const token = "token" in message ? message.token : null;
        if (token?.idToken && token?.issuer) {
          const logoutUrl = new URL(`${token.issuer}/protocol/openid-connect/logout`);
          logoutUrl.searchParams.set("id_token_hint", String(token.idToken));
          try {
            await fetch(logoutUrl.toString());
          } catch (e) {
            console.error("Keycloak logout failed", e);
          }
        }
      },
    },
    callbacks: {
      async jwt({ token, account }) {
        if (account) {
          token.accessToken = account.access_token;
          token.refreshToken = account.refresh_token;
          token.expiresAt = account.expires_at;
          token.idToken = account.id_token;
          // Issuer, ClientId und Origin-Host speichern
          token.issuer = issuer;
          token.clientId = clientId;
          token.originHost = host;
        }

        if (!token.refreshToken) {
          return token;
        }

        const expiresAt = token.expiresAt as number | undefined;
        if (expiresAt && Date.now() < expiresAt * 1000 - 60_000) {
          return token;
        }

        return refreshAccessToken(token);
      },
      async session({ session, token }) {
        session.accessToken = token.accessToken as string | undefined;
        session.idToken = token.idToken as string | undefined;
        session.issuer = token.issuer as string | undefined;
        session.error = token.error as string | undefined;
        return session;
      },
    },
  };
});

async function refreshAccessToken(token: JWT): Promise<JWT> {
  if (!token.issuer || !token.clientId) {
    console.error(
      "Missing issuer/clientId in JWT — token was not properly initialized"
    );
    return { ...token, error: "RefreshAccessTokenError" };
  }

  try {
    const url = `${token.issuer}/protocol/openid-connect/token`;
    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        client_id: token.clientId,
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
