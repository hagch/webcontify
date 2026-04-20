import { z, type ZodTypeAny } from "zod";
import type {
  FieldDefinitionResponse,
  TextFieldConfig,
  IntegerFieldConfig,
  DecimalFieldConfig,
  EnumFieldConfig,
} from "@/generated/content";

/**
 * Builds a Zod schema dynamically from field definitions.
 * MIRROR fields are skipped (read-only, virtual).
 * The schema keys use `cf_{fieldKey}` to match the API value map.
 */
export function buildEntrySchema(
  fields: FieldDefinitionResponse[]
): z.ZodObject<Record<string, ZodTypeAny>> {
  const shape: Record<string, ZodTypeAny> = {};

  for (const field of fields) {
    if (field.fieldType === "MIRROR") continue;

    const key = `cf_${field.fieldKey}`;
    let fieldSchema = buildFieldSchema(field);

    if (!field.required) {
      fieldSchema = fieldSchema.nullable().optional();
    }

    shape[key] = fieldSchema;
  }

  return z.object(shape);
}

function buildFieldSchema(field: FieldDefinitionResponse): ZodTypeAny {
  switch (field.fieldType) {
    case "TEXT":
      return buildTextSchema(field.config as TextFieldConfig | undefined);
    case "INTEGER":
      return buildIntegerSchema(
        field.config as IntegerFieldConfig | undefined
      );
    case "DECIMAL":
      return buildDecimalSchema(
        field.config as DecimalFieldConfig | undefined
      );
    case "BOOLEAN":
      return z.boolean();
    case "DATE":
      return z.string().min(1);
    case "ENUM":
      return buildEnumSchema(field.config as EnumFieldConfig | undefined);
    case "REFERENCE":
      return z.string().uuid();
    default:
      return z.unknown();
  }
}

function buildTextSchema(config?: TextFieldConfig): ZodTypeAny {
  let schema = z.string();

  if (config?.minLength != null) {
    schema = schema.min(config.minLength);
  }
  if (config?.maxLength != null) {
    schema = schema.max(config.maxLength);
  }
  if (config?.pattern) {
    schema = schema.regex(new RegExp(config.pattern));
  }

  return schema;
}

function buildIntegerSchema(config?: IntegerFieldConfig): ZodTypeAny {
  let schema = z.coerce.number().int();

  if (config?.min != null) {
    schema = schema.min(config.min);
  }
  if (config?.max != null) {
    schema = schema.max(config.max);
  }

  return schema;
}

function buildDecimalSchema(config?: DecimalFieldConfig): ZodTypeAny {
  let schema = z.coerce.number();

  if (config?.min != null) {
    schema = schema.min(config.min);
  }
  if (config?.max != null) {
    schema = schema.max(config.max);
  }

  return schema;
}

function buildEnumSchema(config?: EnumFieldConfig): ZodTypeAny {
  if (!config?.allowedValues || config.allowedValues.length === 0) {
    return z.string();
  }

  const [first, ...rest] = config.allowedValues;
  return z.enum([first, ...rest]);
}
