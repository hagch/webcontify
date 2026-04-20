import { z } from "zod";

export const createTenantSchema = z.object({
  name: z.string().min(1, "Name ist erforderlich").max(255),
  adminEmail: z.string().email("Ungültige E-Mail-Adresse").max(255),
  adminFirstName: z.string().min(1, "Vorname ist erforderlich").max(255),
  adminLastName: z.string().min(1, "Nachname ist erforderlich").max(255),
});

export type CreateTenantFormValues = z.infer<typeof createTenantSchema>;
