import { describe, it, expect } from "vitest";
import { buildEntrySchema } from "../build-entry-schema";
import type { FieldDefinitionResponse } from "@/generated/content";

function makeField(
  overrides: Partial<FieldDefinitionResponse> & {
    fieldKey: string;
    fieldType: FieldDefinitionResponse["fieldType"];
  }
): FieldDefinitionResponse {
  return {
    id: "00000000-0000-0000-0000-000000000001",
    required: true,
    unique: false,
    sortable: false,
    filterable: false,
    position: 0,
    ...overrides,
  };
}

describe("buildEntrySchema", () => {
  it("builds schema for TEXT field with min/max length", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({
        fieldKey: "title",
        fieldType: "TEXT",
        config: { fieldType: "TEXT", minLength: 1, maxLength: 100 },
      }),
    ];

    const schema = buildEntrySchema(fields);
    expect(schema.safeParse({ cf_title: "Hello" }).success).toBe(true);
    expect(schema.safeParse({ cf_title: "" }).success).toBe(false);
    expect(schema.safeParse({ cf_title: "x".repeat(101) }).success).toBe(
      false
    );
  });

  it("builds schema for TEXT field with regex pattern", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({
        fieldKey: "code",
        fieldType: "TEXT",
        config: { fieldType: "TEXT", pattern: "^[A-Z]{3}$" },
      }),
    ];

    const schema = buildEntrySchema(fields);
    expect(schema.safeParse({ cf_code: "ABC" }).success).toBe(true);
    expect(schema.safeParse({ cf_code: "abc" }).success).toBe(false);
    expect(schema.safeParse({ cf_code: "ABCD" }).success).toBe(false);
  });

  it("builds schema for INTEGER field with min/max", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({
        fieldKey: "count",
        fieldType: "INTEGER",
        config: { fieldType: "INTEGER", min: 0, max: 100 },
      }),
    ];

    const schema = buildEntrySchema(fields);
    expect(schema.safeParse({ cf_count: 50 }).success).toBe(true);
    expect(schema.safeParse({ cf_count: -1 }).success).toBe(false);
    expect(schema.safeParse({ cf_count: 101 }).success).toBe(false);
    expect(schema.safeParse({ cf_count: 1.5 }).success).toBe(false);
  });

  it("coerces string to INTEGER", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({ fieldKey: "num", fieldType: "INTEGER" }),
    ];

    const schema = buildEntrySchema(fields);
    expect(schema.safeParse({ cf_num: "42" }).success).toBe(true);
  });

  it("builds schema for DECIMAL field", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({
        fieldKey: "price",
        fieldType: "DECIMAL",
        config: { fieldType: "DECIMAL", min: 0 },
      }),
    ];

    const schema = buildEntrySchema(fields);
    expect(schema.safeParse({ cf_price: 19.99 }).success).toBe(true);
    expect(schema.safeParse({ cf_price: -1 }).success).toBe(false);
  });

  it("builds schema for BOOLEAN field", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({ fieldKey: "active", fieldType: "BOOLEAN" }),
    ];

    const schema = buildEntrySchema(fields);
    expect(schema.safeParse({ cf_active: true }).success).toBe(true);
    expect(schema.safeParse({ cf_active: false }).success).toBe(true);
    expect(schema.safeParse({ cf_active: "yes" }).success).toBe(false);
  });

  it("builds schema for DATE field", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({ fieldKey: "born", fieldType: "DATE" }),
    ];

    const schema = buildEntrySchema(fields);
    expect(schema.safeParse({ cf_born: "2026-01-15" }).success).toBe(true);
    expect(schema.safeParse({ cf_born: "" }).success).toBe(false);
  });

  it("builds schema for ENUM field", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({
        fieldKey: "status",
        fieldType: "ENUM",
        config: {
          fieldType: "ENUM",
          allowedValues: ["DRAFT", "ACTIVE", "ARCHIVED"],
        },
      }),
    ];

    const schema = buildEntrySchema(fields);
    expect(schema.safeParse({ cf_status: "DRAFT" }).success).toBe(true);
    expect(schema.safeParse({ cf_status: "INVALID" }).success).toBe(false);
  });

  it("builds schema for REFERENCE field (UUID)", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({
        fieldKey: "author_id",
        fieldType: "REFERENCE",
        config: {
          fieldType: "REFERENCE",
          targetDefinitionId: "00000000-0000-0000-0000-000000000002",
          relationType: "MANY_TO_ONE",
          onDelete: "SET_NULL",
        },
      }),
    ];

    const schema = buildEntrySchema(fields);
    expect(
      schema.safeParse({
        cf_author_id: "550e8400-e29b-41d4-a716-446655440000",
      }).success
    ).toBe(true);
    expect(schema.safeParse({ cf_author_id: "not-a-uuid" }).success).toBe(
      false
    );
  });

  it("skips MIRROR fields", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({ fieldKey: "title", fieldType: "TEXT" }),
      makeField({
        fieldKey: "mirror_field",
        fieldType: "MIRROR",
        config: {
          fieldType: "MIRROR",
          sourceDefinitionId: "00000000-0000-0000-0000-000000000002",
          sourceFieldId: "00000000-0000-0000-0000-000000000003",
          ownerAppId: "00000000-0000-0000-0000-000000000004",
        },
      }),
    ];

    const schema = buildEntrySchema(fields);
    const keys = Object.keys(schema.shape);
    expect(keys).toContain("cf_title");
    expect(keys).not.toContain("cf_mirror_field");
  });

  it("makes optional fields nullable", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({
        fieldKey: "bio",
        fieldType: "TEXT",
        required: false,
      }),
    ];

    const schema = buildEntrySchema(fields);
    expect(schema.safeParse({ cf_bio: null }).success).toBe(true);
    expect(schema.safeParse({ cf_bio: undefined }).success).toBe(true);
    expect(schema.safeParse({}).success).toBe(true);
  });

  it("requires required fields", () => {
    const fields: FieldDefinitionResponse[] = [
      makeField({
        fieldKey: "title",
        fieldType: "TEXT",
        required: true,
      }),
    ];

    const schema = buildEntrySchema(fields);
    expect(schema.safeParse({}).success).toBe(false);
    expect(schema.safeParse({ cf_title: "Hello" }).success).toBe(true);
  });
});
