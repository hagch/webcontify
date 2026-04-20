import { z } from "zod";

export const inviteUserSchema = z.object({
  email: z.string().email("Ungueltige E-Mail-Adresse").max(255),
  firstName: z.string().min(1, "Vorname ist erforderlich").max(255),
  lastName: z.string().min(1, "Nachname ist erforderlich").max(255),
  copyRolesFromUserId: z.string().uuid().optional(),
});

export type InviteUserFormValues = z.infer<typeof inviteUserSchema>;
