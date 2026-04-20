import { z } from "zod";

export const createDefinitionSchema = z.object({
  name: z.string().min(1).max(255),
  description: z.string().max(2000).optional().or(z.literal("")),
});

export const updateDefinitionSchema = z.object({
  name: z.string().min(1).max(255).optional(),
  description: z.string().max(2000).optional().or(z.literal("")),
  displayFormat: z.string().max(500).optional().or(z.literal("")),
});

export const fieldKeySchema = z
  .string()
  .min(1)
  .max(255)
  .regex(
    /^[a-z][a-z0-9_]*$/,
    "Must start with lowercase letter, only a-z, 0-9, _"
  );

export const createFieldSchema = z.object({
  fieldKey: fieldKeySchema,
  fieldType: z.enum(["TEXT", "INTEGER", "DECIMAL", "BOOLEAN", "DATE", "ENUM"]),
  required: z.boolean().default(false),
  unique: z.boolean().default(false),
  sortable: z.boolean().default(false),
  filterable: z.boolean().default(false),
  config: z.record(z.unknown()).optional(),
});

export const connectReferenceSchema = z.object({
  referenceFieldKey: fieldKeySchema,
  mirrorFieldKey: fieldKeySchema,
  relationType: z.enum(["ONE_TO_ONE", "MANY_TO_ONE", "MANY_TO_MANY"]),
  onDelete: z.enum(["CASCADE", "SET_NULL", "RESTRICT"]),
});

export type CreateDefinitionForm = z.infer<typeof createDefinitionSchema>;
export type UpdateDefinitionForm = z.infer<typeof updateDefinitionSchema>;
export type CreateFieldForm = z.infer<typeof createFieldSchema>;
export type ConnectReferenceForm = z.infer<typeof connectReferenceSchema>;
