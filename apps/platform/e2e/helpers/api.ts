const API_BASE = process.env.PLAYWRIGHT_API_URL ?? "http://localhost:8080/api/v1";

export interface TenantData {
  id: string;
  name: string;
  slug: string;
  status: string;
}

export async function createTenant(
  name: string,
  slug: string
): Promise<TenantData> {
  const response = await fetch(`${API_BASE}/admin/tenants`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
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
  const response = await fetch(`${API_BASE}/admin/tenants/${tenantId}`, {
    method: "DELETE",
  });
  if (!response.ok && response.status !== 404) {
    throw new Error(`Failed to delete tenant: ${response.status}`);
  }
}
