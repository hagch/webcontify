"use client";

import type { Control, FieldValues } from "react-hook-form";
import type {
  FieldDefinitionResponse,
  TextFieldConfig,
  IntegerFieldConfig,
  DecimalFieldConfig,
  DateFieldConfig,
  EnumFieldConfig,
  ReferenceFieldConfig,
  ContentDefinitionResponse,
} from "@/generated/content";
import { TextFieldInput } from "./inputs/text-field-input";
import { IntegerFieldInput } from "./inputs/integer-field-input";
import { DecimalFieldInput } from "./inputs/decimal-field-input";
import { BooleanFieldInput } from "./inputs/boolean-field-input";
import { DateFieldInput } from "./inputs/date-field-input";
import { EnumFieldInput } from "./inputs/enum-field-input";
import { ReferenceFieldInput } from "./inputs/reference-field-input";

interface DynamicFieldRendererProps {
  field: FieldDefinitionResponse;
  control: Control<FieldValues>;
  appSlug: string;
  /** All definitions in context, used to resolve REFERENCE target slugs */
  allDefinitions?: ContentDefinitionResponse[];
  /** Raw entry values from the API (used to extract resolved reference display texts) */
  entryValues?: Record<string, unknown>;
}

/**
 * Dispatches to the correct field input component based on field type.
 * MIRROR fields return null (read-only, not editable in forms).
 */
export function DynamicFieldRenderer({
  field,
  control,
  appSlug,
  allDefinitions,
  entryValues,
}: DynamicFieldRendererProps) {
  const key = `cf_${field.fieldKey}`;
  const label = field.fieldKey;

  switch (field.fieldType) {
    case "TEXT": {
      const config = field.config as TextFieldConfig | undefined;
      return (
        <TextFieldInput
          control={control}
          name={key}
          label={label}
          required={field.required}
          maxLength={config?.maxLength}
        />
      );
    }
    case "INTEGER": {
      const config = field.config as IntegerFieldConfig | undefined;
      return (
        <IntegerFieldInput
          control={control}
          name={key}
          label={label}
          required={field.required}
          min={config?.min}
          max={config?.max}
        />
      );
    }
    case "DECIMAL": {
      const config = field.config as DecimalFieldConfig | undefined;
      return (
        <DecimalFieldInput
          control={control}
          name={key}
          label={label}
          required={field.required}
          min={config?.min}
          max={config?.max}
          scale={config?.scale}
        />
      );
    }
    case "BOOLEAN":
      return (
        <BooleanFieldInput
          control={control}
          name={key}
          label={label}
          required={field.required}
        />
      );
    case "DATE": {
      const config = field.config as DateFieldConfig | undefined;
      return (
        <DateFieldInput
          control={control}
          name={key}
          label={label}
          required={field.required}
          dateFormat={config?.dateFormat}
        />
      );
    }
    case "ENUM": {
      const config = field.config as EnumFieldConfig | undefined;
      return (
        <EnumFieldInput
          control={control}
          name={key}
          label={label}
          required={field.required}
          allowedValues={config?.allowedValues ?? []}
        />
      );
    }
    case "REFERENCE": {
      const config = field.config as ReferenceFieldConfig | undefined;
      const targetDef = allDefinitions?.find(
        (d) => d.id === config?.targetDefinitionId
      );
      const targetDefSlug = targetDef?.slug ?? "";
      // Extract display text from resolved reference value (backend returns { id, display })
      const rawRef = entryValues?.[key];
      const initialDisplay =
        typeof rawRef === "object" && rawRef !== null && "display" in rawRef
          ? (rawRef as { display: string }).display
          : undefined;
      return (
        <ReferenceFieldInput
          control={control}
          name={key}
          label={label}
          required={field.required}
          appSlug={appSlug}
          targetDefSlug={targetDefSlug}
          initialDisplay={initialDisplay}
        />
      );
    }
    case "MIRROR":
      return null;
    default:
      return null;
  }
}
