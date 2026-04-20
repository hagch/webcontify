import { z } from "zod";

export const createTenantSchema = z.object({
  name: z
    .string()
    .min(1, "Name is required")
    .max(255, "Name must be 255 characters or less"),
  slug: z
    .string()
    .min(1, "Slug is required")
    .max(100, "Slug must be 100 characters or less")
    .regex(
      /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/,
      "Slug must be lowercase, alphanumeric, with hyphens only"
    ),
});

export type CreateTenantInput = z.infer<typeof createTenantSchema>;
