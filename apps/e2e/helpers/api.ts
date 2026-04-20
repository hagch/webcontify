const API_BASE = process.env.API_URL ?? "http://localhost:8080/api/v1";
const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? "http://auth.localhost";

export interface CreateTenantOptions {
  adminEmail?: string;
  adminFirstName?: string;
  adminLastName?: string;
}

interface CleanupEntry {
  type: "tenant" | "keycloak-user";
  id: string;
  realm?: string;
}

export interface TenantData {
  id: string;
  name: string;
  slug: string;
  status: string;
}

/**
 * API client for E2E tests.
 *
 * Provides convenience methods for common API operations and manages
 * a stack-based cleanup registry so that resources created during a test
 * are automatically deleted in reverse order when cleanup() is called.
 */
export class ApiClient {
  private cleanupStack: CleanupEntry[] = [];
  private token: string | null = null;

  /**
   * Returns a cached platform admin token, fetching one if necessary.
   */
  async getToken(): Promise<string> {
    if (this.token) return this.token;
    const response = await fetch(
      `${KEYCLOAK_URL}/realms/webcontify-platform/protocol/openid-connect/token`,
      {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "password",
          client_id: "platform-ui",
          username: process.env.PLATFORM_ADMIN_EMAIL ?? "admin",
          password: process.env.PLATFORM_ADMIN_PASSWORD ?? "admin",
        }),
      }
    );
    if (!response.ok) {
      throw new Error(`Failed to get admin token: ${response.status}`);
    }
    const data = await response.json();
    this.token = data.access_token as string;
    return this.token;
  }

  /**
   * Creates a tenant via the admin API and registers it for cleanup.
   * Optionally accepts admin user fields required by the backend.
   * Defaults are generated from the tenant name when not provided.
   */
  async createTenant(name: string, options?: CreateTenantOptions): Promise<TenantData> {
    const token = await this.getToken();
    const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
    const body = {
      name,
      adminEmail: options?.adminEmail ?? `admin-${slug}-${Date.now()}@e2e.test`,
      adminFirstName: options?.adminFirstName ?? "Admin",
      adminLastName: options?.adminLastName ?? "E2E",
    };
    const response = await fetch(`${API_BASE}/admin/tenants`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      throw new Error(
        `Failed to create tenant: ${response.status} ${await response.text()}`
      );
    }
    const data = await response.json();
    this.cleanupStack.push({ type: "tenant", id: data.id });
    return data;
  }

  /**
   * Polls GET /api/v1/admin/tenants/{id} until status is "ACTIVE".
   * Retries every 1 s, times out after 30 s by default.
   */
  async waitForTenantProvisioning(tenantId: string, timeoutMs = 60_000): Promise<void> {
    const token = await this.getToken();
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const response = await fetch(`${API_BASE}/admin/tenants/${tenantId}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!response.ok) {
        throw new Error(
          `Failed to fetch tenant status: ${response.status} ${await response.text()}`
        );
      }
      const tenant: TenantData = await response.json();
      if (tenant.status === "ACTIVE") return;
      await new Promise((resolve) => setTimeout(resolve, 1_000));
    }
    throw new Error(
      `Tenant ${tenantId} did not reach ACTIVE status within ${timeoutMs}ms`
    );
  }

  /**
   * Deletes a tenant by ID. Ignores 404.
   */
  async deleteTenant(tenantId: string): Promise<void> {
    const token = await this.getToken();
    const response = await fetch(`${API_BASE}/admin/tenants/${tenantId}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!response.ok && response.status !== 404) {
      throw new Error(`Failed to delete tenant: ${response.status}`);
    }
  }

  /**
   * Lists all roles for the authenticated tenant user.
   * Requires a tenant-scoped JWT and the tenant slug header.
   */
  async listRoles(token: string, tenantSlug: string): Promise<Array<{ id: string; name: string; systemRole: boolean }>> {
    const response = await fetch(`${API_BASE}/tenant/roles`, {
      headers: {
        Authorization: `Bearer ${token}`,
        "X-Tenant-Slug": tenantSlug,
      },
    });
    if (!response.ok) {
      throw new Error(`Failed to list roles: ${response.status} ${await response.text()}`);
    }
    const data = await response.json();
    return data.roles ?? data;
  }

  /**
   * Creates a custom role for the tenant.
   */
  async createRole(
    token: string,
    tenantSlug: string,
    body: { name: string; description?: string }
  ): Promise<{ id: string; name: string; systemRole: boolean }> {
    const response = await fetch(`${API_BASE}/tenant/roles`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        "X-Tenant-Slug": tenantSlug,
      },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      throw new Error(`Failed to create role: ${response.status} ${await response.text()}`);
    }
    return response.json();
  }

  /**
   * Sets permissions on a custom role via PUT /api/v1/tenant/roles/{roleId}/permissions.
   * Only callable by users with role_management:editor permission.
   */
  async setRolePermissions(
    token: string,
    tenantSlug: string,
    roleId: string,
    body: {
      permissions: Array<{ resourceType: string; actions: string[] }>;
    }
  ): Promise<void> {
    const response = await fetch(`${API_BASE}/tenant/roles/${roleId}/permissions`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        "X-Tenant-Slug": tenantSlug,
      },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      throw new Error(
        `Failed to set role permissions: ${response.status} ${await response.text()}`
      );
    }
  }

  /**
   * Invites a user to the tenant.
   */
  async inviteUser(
    token: string,
    tenantSlug: string,
    body: { email: string; firstName: string; lastName: string; roleIds: string[] }
  ): Promise<{ id: string; email: string }> {
    const response = await fetch(`${API_BASE}/tenant/users`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        "X-Tenant-Slug": tenantSlug,
      },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      throw new Error(`Failed to invite user: ${response.status} ${await response.text()}`);
    }
    return response.json();
  }

  /**
   * Registers an arbitrary resource for cleanup without performing an API call now.
   * Useful when a resource was created outside of this client but should still be
   * cleaned up at the end of the test.
   */
  registerForCleanup(entry: CleanupEntry): void {
    this.cleanupStack.push(entry);
  }

  /**
   * Deletes all registered resources in reverse order (LIFO).
   * Errors during individual cleanups are logged as warnings and do not abort
   * the remaining cleanup steps.
   */
  async cleanup(): Promise<void> {
    for (const item of [...this.cleanupStack].reverse()) {
      try {
        switch (item.type) {
          case "keycloak-user": {
            const { getKeycloakAdminToken, deleteKeycloakUser } = await import(
              "./keycloak-admin"
            );
            const adminToken = await getKeycloakAdminToken();
            await deleteKeycloakUser(adminToken, item.realm!, item.id);
            break;
          }
          case "tenant":
            await this.deleteTenant(item.id);
            break;
        }
      } catch (e) {
        console.warn(`Cleanup failed for ${item.type}:${item.id}`, e);
      }
    }
    this.cleanupStack = [];
  }
}
