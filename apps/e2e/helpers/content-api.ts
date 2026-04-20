/**
 * Content Definition API helpers for E2E tests.
 *
 * Provides convenience methods for creating, modifying and deleting
 * content definitions and fields via the Content API.
 * Follows the same pattern as the main ApiClient in api.ts.
 */

const API_BASE = process.env.API_URL ?? "http://localhost:8080/api/v1";

export interface ContentDefinitionData {
  id: string;
  appId: string;
  name: string;
  slug: string;
  description?: string;
  fields: FieldDefinitionData[];
}

export interface FieldDefinitionData {
  id: string;
  fieldKey: string;
  fieldType: string;
  required: boolean;
  unique: boolean;
  sortable: boolean;
  filterable: boolean;
  position: number;
  config?: Record<string, unknown>;
}

/**
 * Creates a content definition via the API.
 *
 * @param token  - Tenant-scoped JWT
 * @param tenantSlug - Tenant slug for the X-Tenant-Slug header
 * @param appSlug - App slug used in the URL path
 * @param data - Definition name and optional description
 * @returns The created definition
 */
export async function createContentDefinitionViaApi(
  token: string,
  tenantSlug: string,
  appSlug: string,
  data: { name: string; description?: string }
): Promise<ContentDefinitionData> {
  const response = await fetch(
    `${API_BASE}/content/${appSlug}/definitions`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        "X-Tenant-Slug": tenantSlug,
      },
      body: JSON.stringify(data),
    }
  );
  if (!response.ok) {
    throw new Error(
      `Failed to create content definition: ${response.status} ${await response.text()}`
    );
  }
  return response.json();
}

/**
 * Adds a field to a content definition via the API.
 *
 * @param token - Tenant-scoped JWT
 * @param tenantSlug - Tenant slug for the X-Tenant-Slug header
 * @param appSlug - App slug used in the URL path
 * @param definitionId - Target definition UUID
 * @param data - Field key, type, and optional config
 * @returns The created field
 */
export async function addFieldViaApi(
  token: string,
  tenantSlug: string,
  appSlug: string,
  definitionId: string,
  data: {
    fieldKey: string;
    fieldType: string;
    required?: boolean;
    unique?: boolean;
    sortable?: boolean;
    filterable?: boolean;
    config?: Record<string, unknown>;
  }
): Promise<FieldDefinitionData> {
  const response = await fetch(
    `${API_BASE}/content/${appSlug}/definitions/${definitionId}/fields`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        "X-Tenant-Slug": tenantSlug,
      },
      body: JSON.stringify(data),
    }
  );
  if (!response.ok) {
    throw new Error(
      `Failed to add field: ${response.status} ${await response.text()}`
    );
  }
  return response.json();
}

/**
 * Deletes a content definition via the API. Ignores 404.
 *
 * @param token - Tenant-scoped JWT
 * @param tenantSlug - Tenant slug for the X-Tenant-Slug header
 * @param appSlug - App slug used in the URL path
 * @param definitionId - Definition UUID to delete
 */
export async function deleteContentDefinitionViaApi(
  token: string,
  tenantSlug: string,
  appSlug: string,
  definitionId: string
): Promise<void> {
  const response = await fetch(
    `${API_BASE}/content/${appSlug}/definitions/${definitionId}`,
    {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${token}`,
        "X-Tenant-Slug": tenantSlug,
      },
    }
  );
  if (!response.ok && response.status !== 404) {
    throw new Error(
      `Failed to delete content definition: ${response.status} ${await response.text()}`
    );
  }
}

/**
 * Deletes a field from a content definition via the API. Ignores 404.
 *
 * @param token - Tenant-scoped JWT
 * @param tenantSlug - Tenant slug for the X-Tenant-Slug header
 * @param appSlug - App slug used in the URL path
 * @param definitionId - Parent definition UUID
 * @param fieldId - Field UUID to delete
 */
export async function deleteFieldViaApi(
  token: string,
  tenantSlug: string,
  appSlug: string,
  definitionId: string,
  fieldId: string
): Promise<void> {
  const response = await fetch(
    `${API_BASE}/content/${appSlug}/definitions/${definitionId}/fields/${fieldId}`,
    {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${token}`,
        "X-Tenant-Slug": tenantSlug,
      },
    }
  );
  if (!response.ok && response.status !== 404) {
    throw new Error(
      `Failed to delete field: ${response.status} ${await response.text()}`
    );
  }
}
