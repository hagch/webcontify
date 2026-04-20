import { z } from "zod";

export const createTenantSchema = z.object({
  name: z.string().min(1).max(255),
  slug: z
    .string()
    .min(1)
    .max(100)
    .regex(/^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/),
});

export type CreateTenantFormData = z.infer<typeof createTenantSchema>;

export const updateTenantSchema = z.object({
  name: z.string().min(1).max(255),
  slug: z
    .string()
    .min(1)
    .max(100)
    .regex(/^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/),
});

export type UpdateTenantFormData = z.infer<typeof updateTenantSchema>;
