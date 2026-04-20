import { z } from "zod";

export const updateProfileSchema = z.object({
  firstName: z.string().min(1).max(255),
  lastName: z.string().min(1).max(255),
});

export type UpdateProfileFormValues = z.infer<typeof updateProfileSchema>;
