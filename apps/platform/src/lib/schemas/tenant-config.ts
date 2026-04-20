import { z } from "zod";

const hexColorField = z
  .string()
  .regex(/^#[0-9a-fA-F]{6}$/, "validation.hexColor")
  .optional()
  .or(z.literal(""));

export const updateBrandingSchema = z.object({
  logoStorageKey: z.string().optional().or(z.literal("")),
  faviconStorageKey: z.string().optional().or(z.literal("")),
  primaryColor: hexColorField,
  secondaryColor: hexColorField,
  accentColor: hexColorField,
  dangerColor: hexColorField,
  successColor: hexColorField,
  warningColor: hexColorField,
  fontFamily: z.enum(["system", "inter", "geist", "plus-jakarta-sans", "dm-sans", "roboto", "source-sans-3", "ibm-plex-sans", "open-sans", "lato", "nunito-sans", "poppins", "merriweather"]).optional().or(z.literal("")),
  borderRadius: z.enum(["0", "0.25rem", "0.5rem", "0.75rem", "1rem"]).optional().or(z.literal("")),
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
