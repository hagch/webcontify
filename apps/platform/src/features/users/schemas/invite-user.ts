import { z } from "zod";

export const inviteUserSchema = z.object({
  email: z.string().email().max(255),
  firstName: z.string().min(1).max(255),
  lastName: z.string().min(1).max(255),
  roleIds: z.array(z.string().uuid()).min(1),
  copyRolesFromUserId: z.string().uuid().optional(),
});

export type InviteUserFormValues = z.infer<typeof inviteUserSchema>;
