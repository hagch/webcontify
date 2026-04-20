import { z } from "zod";

export const updateBrandingSchema = z.object({
  logoStorageKey: z.string().optional().or(z.literal("")),
  faviconStorageKey: z.string().optional().or(z.literal("")),
  primaryColor: z
    .string()
    .regex(/^#[0-9a-fA-F]{6}$/, "Bitte einen gueltigen Hex-Farbwert eingeben (z.B. #6366F1)")
    .optional()
    .or(z.literal("")),
  secondaryColor: z
    .string()
    .regex(/^#[0-9a-fA-F]{6}$/, "Bitte einen gueltigen Hex-Farbwert eingeben (z.B. #6366F1)")
    .optional()
    .or(z.literal("")),
});
export type UpdateBrandingFormData = z.infer<typeof updateBrandingSchema>;

export const updateEmailSchema = z.object({
  smtpHost: z.string().min(1),
  smtpPort: z.number().min(1).max(65535),
  smtpUser: z.string().optional().or(z.literal("")),
  smtpPassword: z.string().optional().or(z.literal("")),
  fromAddress: z.string().email(),
  fromName: z.string().min(1),
});
export type UpdateEmailFormData = z.infer<typeof updateEmailSchema>;

export const updateDomainSchema = z.object({
  customDomain: z.string().optional().or(z.literal("")),
});
export type UpdateDomainFormData = z.infer<typeof updateDomainSchema>;

export const testEmailSchema = z.object({
  recipientEmail: z.string().email(),
});
export type TestEmailFormData = z.infer<typeof testEmailSchema>;
