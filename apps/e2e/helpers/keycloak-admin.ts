/**
 * Keycloak Admin API helpers for E2E tests.
 *
 * All functions work directly against the Keycloak Admin REST API.
 * No Playwright Page object required — pure HTTP calls via fetch.
 *
 * Keycloak Admin URL: auth.localhost (via Traefik)
 * Master realm admin: admin / admin
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
  /** Tenant ID set as a user attribute so the JWT claim is correct */
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
 * Fetches an admin token from the master realm.
 * Uses Resource Owner Password Credentials (admin-cli is confidential-exempt).
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
      `Failed to get Keycloak admin token: ${response.status} ${body}`
    );
  }

  const data = await response.json();
  return data.access_token as string;
}

/**
 * Creates a user in the specified realm.
 * Optionally sets tenant_id as a user attribute.
 *
 * Note: Realm roles cannot be assigned directly during creation.
 * Use assignRealmRole() after creation to assign roles.
 *
 * @returns The Keycloak user ID (UUID)
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

  // Set tenant_id as a user attribute (Keycloak expects string arrays)
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
      `Failed to create Keycloak user: ${response.status} ${responseBody}`
    );
  }

  // Keycloak returns no body, but provides the Location header with the user ID
  const location = response.headers.get("Location");
  if (!location) {
    throw new Error("Keycloak did not return a Location header");
  }

  // Location: .../admin/realms/{realm}/users/{userId}
  const userId = location.split("/").at(-1);
  if (!userId) {
    throw new Error(
      `Failed to extract user ID from Location header: ${location}`
    );
  }

  return userId;
}

/**
 * Finds a user by username.
 * Returns null if no user is found.
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
      `User search failed: ${response.status} ${await response.text()}`
    );
  }

  const users: KeycloakUser[] = await response.json();
  return users.length > 0 ? users[0] : null;
}

/**
 * Assigns a realm role to a user.
 * Fetches role details first since Keycloak requires the full role representation.
 */
export async function assignRealmRole(
  token: string,
  realmName: string,
  userId: string,
  roleName: string
): Promise<void> {
  // Fetch role by name
  const roleUrl = `${KEYCLOAK_URL}/admin/realms/${realmName}/roles/${encodeURIComponent(roleName)}`;
  const roleResponse = await fetch(roleUrl, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!roleResponse.ok) {
    throw new Error(
      `Role ${roleName} not found: ${roleResponse.status}`
    );
  }

  const role = await roleResponse.json();

  // Assign the role to the user
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
      `Failed to assign role: ${assignResponse.status}`
    );
  }
}

/**
 * Sets a user's password directly via the Keycloak Admin API.
 * Useful in E2E tests to bypass the invite/UPDATE_PASSWORD flow.
 */
export async function setUserPassword(
  token: string,
  realm: string,
  userId: string,
  password: string
): Promise<void> {
  const url = `${KEYCLOAK_URL}/admin/realms/${realm}/users/${userId}/reset-password`;
  const response = await fetch(url, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ type: "password", value: password, temporary: false }),
  });
  if (!response.ok) {
    throw new Error(
      `Failed to set password for user ${userId}: ${response.status} ${await response.text()}`
    );
  }
}

/**
 * Clears all required actions for a user so they can log in without
 * being forced to change their password or complete other actions.
 */
export async function clearRequiredActions(
  token: string,
  realm: string,
  userId: string
): Promise<void> {
  const url = `${KEYCLOAK_URL}/admin/realms/${realm}/users/${userId}`;
  const getResponse = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!getResponse.ok) {
    throw new Error(
      `Failed to fetch user ${userId}: ${getResponse.status} ${await getResponse.text()}`
    );
  }
  const user = await getResponse.json();
  user.requiredActions = [];
  const putResponse = await fetch(url, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(user),
  });
  if (!putResponse.ok) {
    throw new Error(
      `Failed to clear required actions for user ${userId}: ${putResponse.status} ${await putResponse.text()}`
    );
  }
}

/**
 * Finds a user by email address.
 * Returns null if no user is found.
 */
export async function findKeycloakUserByEmail(
  token: string,
  realmName: string,
  email: string
): Promise<KeycloakUser | null> {
  const url = `${KEYCLOAK_URL}/admin/realms/${realmName}/users?email=${encodeURIComponent(email)}&exact=true`;
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(
      `User search by email failed: ${response.status} ${await response.text()}`
    );
  }
  const users: KeycloakUser[] = await response.json();
  return users.length > 0 ? users[0] : null;
}

/**
 * Deletes a user from the realm.
 * Ignores 404 (user does not exist).
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
      `Failed to delete user: ${response.status} ${await response.text()}`
    );
  }
}
