/**
 * Keycloak Admin API Helper fuer E2E Tests.
 *
 * Alle Funktionen arbeiten direkt gegen die Keycloak Admin REST API.
 * Kein Playwright Page-Objekt noetig — reine HTTP-Aufrufe via fetch.
 *
 * Keycloak Admin URL: auth.localhost (via Traefik)
 * Master Realm Admin: admin / admin
 */

const KEYCLOAK_URL =
  process.env.KEYCLOAK_URL ?? "http://auth.localhost";

const MASTER_REALM = "master";
const ADMIN_CLIENT_ID = "admin-cli";
const ADMIN_USERNAME = process.env.KEYCLOAK_ADMIN_USERNAME ?? "admin";
const ADMIN_PASSWORD = process.env.KEYCLOAK_ADMIN_PASSWORD ?? "admin";

export interface KeycloakUserData {
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  password: string;
  /** Tenant-ID wird als User-Attribut gesetzt, damit der JWT-Claim stimmt */
  tenantId?: string;
  realmRoles?: string[];
}

export interface KeycloakUser {
  id: string;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  enabled: boolean;
  attributes?: Record<string, string[]>;
}

/**
 * Holt ein Admin-Token vom Master Realm.
 * Verwendet Resource Owner Password Credentials (admin-cli ist confidential-exempt).
 */
export async function getKeycloakAdminToken(): Promise<string> {
  const url = `${KEYCLOAK_URL}/realms/${MASTER_REALM}/protocol/openid-connect/token`;

  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "password",
      client_id: ADMIN_CLIENT_ID,
      username: ADMIN_USERNAME,
      password: ADMIN_PASSWORD,
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(
      `Keycloak Admin Token konnte nicht geholt werden: ${response.status} ${body}`
    );
  }

  const data = await response.json();
  return data.access_token as string;
}

/**
 * Erstellt einen User im angegebenen Realm.
 * Setzt optional tenant_id als User-Attribut.
 *
 * Hinweis: Realm Roles koennen nicht direkt beim Erstellen gesetzt werden.
 * Verwende assignRealmRole() nach dem Erstellen um Rollen zuzuweisen.
 *
 * @returns Die Keycloak User-ID (UUID)
 */
export async function createKeycloakUser(
  token: string,
  realmName: string,
  userData: KeycloakUserData
): Promise<string> {
  const url = `${KEYCLOAK_URL}/admin/realms/${realmName}/users`;

  const body: Record<string, unknown> = {
    username: userData.username,
    email: userData.email,
    firstName: userData.firstName ?? "",
    lastName: userData.lastName ?? "",
    emailVerified: true,
    enabled: true,
    credentials: [
      {
        type: "password",
        value: userData.password,
        temporary: false,
      },
    ],
  };

  // tenant_id als User-Attribut setzen (Keycloak erwartet String-Arrays)
  if (userData.tenantId) {
    body.attributes = {
      tenant_id: [userData.tenantId],
    };
  }

  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    const responseBody = await response.text();
    throw new Error(
      `Keycloak User konnte nicht erstellt werden: ${response.status} ${responseBody}`
    );
  }

  // Keycloak gibt keine Body zurueck, aber den Location Header mit der User-ID
  const location = response.headers.get("Location");
  if (!location) {
    throw new Error("Keycloak hat keinen Location Header zurueckgegeben");
  }

  // Location: .../admin/realms/{realm}/users/{userId}
  const userId = location.split("/").at(-1);
  if (!userId) {
    throw new Error(`Konnte User-ID aus Location nicht extrahieren: ${location}`);
  }

  return userId;
}

/**
 * Findet einen User anhand des Usernamens.
 * Gibt null zurueck wenn kein User gefunden.
 */
export async function findKeycloakUser(
  token: string,
  realmName: string,
  username: string
): Promise<KeycloakUser | null> {
  const url = `${KEYCLOAK_URL}/admin/realms/${realmName}/users?username=${encodeURIComponent(username)}&exact=true`;

  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    throw new Error(
      `User-Suche fehlgeschlagen: ${response.status} ${await response.text()}`
    );
  }

  const users: KeycloakUser[] = await response.json();
  return users.length > 0 ? users[0] : null;
}

/**
 * Weist einem User eine Realm-Rolle zu.
 * Holt zuerst die Rollen-Details, da Keycloak die vollstaendige Rollen-Representation braucht.
 */
export async function assignRealmRole(
  token: string,
  realmName: string,
  userId: string,
  roleName: string
): Promise<void> {
  // Rolle per Name abrufen
  const roleUrl = `${KEYCLOAK_URL}/admin/realms/${realmName}/roles/${encodeURIComponent(roleName)}`;
  const roleResponse = await fetch(roleUrl, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!roleResponse.ok) {
    throw new Error(
      `Rolle ${roleName} nicht gefunden: ${roleResponse.status}`
    );
  }

  const role = await roleResponse.json();

  // Rolle dem User zuweisen
  const assignUrl = `${KEYCLOAK_URL}/admin/realms/${realmName}/users/${userId}/role-mappings/realm`;
  const assignResponse = await fetch(assignUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify([role]),
  });

  if (!assignResponse.ok) {
    throw new Error(
      `Rolle konnte nicht zugewiesen werden: ${assignResponse.status}`
    );
  }
}

/**
 * Loescht einen User aus dem Realm.
 * Ignoriert 404 (User existiert nicht).
 */
export async function deleteKeycloakUser(
  token: string,
  realmName: string,
  userId: string
): Promise<void> {
  const url = `${KEYCLOAK_URL}/admin/realms/${realmName}/users/${userId}`;

  const response = await fetch(url, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok && response.status !== 404) {
    throw new Error(
      `User konnte nicht geloescht werden: ${response.status} ${await response.text()}`
    );
  }
}
