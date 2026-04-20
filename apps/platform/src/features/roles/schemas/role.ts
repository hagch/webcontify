import { z } from "zod";

export const createRoleSchema = z.object({
  name: z.string().min(1, "Name ist erforderlich").max(255),
  description: z.string().max(1000).optional(),
});

export type CreateRoleFormValues = z.infer<typeof createRoleSchema>;

export const updateRoleSchema = createRoleSchema;
export type UpdateRoleFormValues = z.infer<typeof updateRoleSchema>;
