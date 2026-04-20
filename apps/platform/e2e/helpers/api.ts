const API_BASE = process.env.PLAYWRIGHT_API_URL ?? "http://localhost:8080/api/v1";

const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? "http://auth.localhost";

export interface TenantData {
  id: string;
  name: string;
  slug: string;
  status: string;
}

/**
 * Holt einen Admin-Token fuer API-Aufrufe (Platform Admin).
 */
async function getApiAdminToken(): Promise<string> {
  const response = await fetch(
    `${KEYCLOAK_URL}/realms/webcontify-platform/protocol/openid-connect/token`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "password",
        client_id: "platform-ui",
        username: "admin",
        password: "admin",
      }),
    }
  );
  if (!response.ok) {
    throw new Error(`Failed to get admin token: ${response.status}`);
  }
  const data = await response.json();
  return data.access_token as string;
}

export async function createTenant(
  name: string,
  slug: string
): Promise<TenantData> {
  const token = await getApiAdminToken();
  const response = await fetch(`${API_BASE}/admin/tenants`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ name, slug }),
  });
  if (!response.ok) {
    throw new Error(
      `Failed to create tenant: ${response.status} ${await response.text()}`
    );
  }
  return response.json();
}

export async function deleteTenant(tenantId: string): Promise<void> {
  const token = await getApiAdminToken();
  const response = await fetch(`${API_BASE}/admin/tenants/${tenantId}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok && response.status !== 404) {
    throw new Error(`Failed to delete tenant: ${response.status}`);
  }
}
