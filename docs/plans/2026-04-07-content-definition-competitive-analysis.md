# Content Definition — Competitive Analysis

> Companion document to `2026-04-06-content-definition-design.md`.
> Compares our Content Definition approach against established CMS/Low-Code platforms.

## 1. Compared Systems

| System | Type | Schema Approach | Visual Editor |
|--------|------|----------------|---------------|
| Strapi | Headless CMS | Form-based, ~20 field types | No graph |
| Contentful | Headless CMS | Form-based + Visual Modeler (read-only graph) | Graph view-only |
| Directus | Headless CMS | DB introspection + form-based, ~30 field types | Graph view (v11+) |
| Sanity | Headless CMS | Code-first (TypeScript schemas) | No visual editor |
| Payload CMS | Headless CMS | Config-based (TypeScript), ~20 field types | No visual editor |
| Airtable | Spreadsheet DB | Spreadsheet-first, linked records | Schema Extension (read-only) |
| NocoDB | Spreadsheet DB | DB introspection + spreadsheet, linked records | No graph |
| Appsmith/Retool | Low-Code UI Builder | No own data model, connects to external DBs | No schema editor |

## 2. What We Do Better

### App-Scoping with Dependency Management
No other CMS has modular package structures with versionable snapshots and dependency graphs. Strapi, Contentful, Directus — all have flat, project-level content types. Our App-scoping enables marketplace-ready content packages.

### MIRROR Fields with Ownership
Airtable/NocoDB have bidirectional linked records (since 2012), but without cross-app semantics. Strapi creates inverse fields but without ownership. Our `ownerAppId` approach prevents circular cross-app dependencies architecturally. This is **novel** — no other tool has "the MIRROR field belongs to the source app, not the target app."

### ReBAC Permissions (OpenFGA)
Inheritance chain Tenant → App → Definition is significantly more powerful than Strapi Enterprise RBAC or Directus collection-level permissions. Declarative, auditable, fine-grained.

### Schema-Level Undo/Redo
None of the compared CMS have undo/redo for schema changes. Contentful has content entry versioning only. Strapi schema changes are final until redeployment. Our revision-based approach (JSON Patch + inverse patch via ChangeRecordService) is unique.

### Unified Join Table for Relations
`content_reference` table works uniformly for 1:1, N:1, N:M. Fits perfectly with the EAV system. MIRROR queries are trivial (same table, reversed direction). Strapi/Directus use inline FK for M2O and separate junction tables for M2M — two query patterns.

### Graph as Editor, Not Just Visualization
Contentful has a Visual Modeler, but editing happens in forms. Our canvas IS the editor. Drag-and-connect for references, popovers for field config, spotlight search for discovery — all in one view.

## 3. What Others Do Better (Gaps)

### 3.1 Field Type Coverage (Critical)

| Field Type | Strapi | Contentful | Directus | Payload | Us |
|------------|--------|------------|----------|---------|-----|
| Rich Text (structured) | Yes | Yes | Yes (WYSIWYG) | Yes (Lexical) | **No** |
| File/Media/Image | Yes | Yes | Yes | Yes (Upload) | **No** |
| JSON/Object | Yes | Yes | Yes | Yes | **No** |
| Array/List | Yes | Yes | Yes | Yes | **No** |
| Component/Group (nested) | Yes (Components) | No | No | Yes (Group, Blocks) | **No** |
| Dynamic Zones/Blocks | Yes | No | No | Yes (Blocks) | **No** |
| Computed/Formula | No | No | No | No | **No** |
| Lookup/Rollup | No | No | No | No | **No** |

**Assessment:** 8 types (incl. REFERENCE + MIRROR) vs. industry standard 15-25. Rich Text and File Upload are absolutely essential for real-world use cases. Array/List enables repeatable structures within an entry (e.g. tags without a separate definition).

**Recommendation:** Plan Rich Text, File/Media, JSON, Array/List as follow-up phases. Component/Group fields for nested structures.

### 3.2 Nested/Composite Structures (High)

Strapi has Components (reusable field groups), Payload has Group + Blocks, Sanity has Object Types with arbitrary nesting. We only have flat fields. This leads to definition explosion — "Address" becomes its own definition with REFERENCE instead of being a field group.

**Recommendation:** Component/Group field type in roadmap. Enables reusable structures like Address, SEO Meta, Social Links.

### 3.3 Computed/Lookup/Rollup Fields (Medium)

Airtable/NocoDB killer feature. "Show author name on Post" (Lookup), "Count of posts per User" (Rollup), "Order total = sum of line items" (Formula). Not the same as our `displayFormat` — that is a display template, not a computed field value.

**Recommendation:** Roadmap item. Not needed for Phase 5, but important for Content Management phase.

### 3.4 Field-Level Permissions (Medium)

Directus can restrict read/write per field. We only have definition-level permissions. For sensitive fields (e.g. "salary" in an HR app), field-level permissions are important.

**Recommendation:** Already planned. Extend OpenFGA model when needed.

### 3.5 Conditional Field Visibility (Low for Phase 5)

"Show field X only when field Y = value Z" in content entry forms. Contentful supports this. Relevant only when Content Management (entry editing) is implemented.

**Recommendation:** Roadmap for Content Management phase.

### 3.6 Database Introspection (Low Priority)

Directus and NocoDB can sit on top of existing databases. We are greenfield-only. This excludes use cases where existing data needs to be structured.

**Recommendation:** Not a priority. Our platform creates its own data structures.

## 4. Cross-Cutting Concerns

### Canvas-Only Editing (No Sidebar/Detail Page)

**Status:** No established CMS uses canvas-only for schema editing. Contentful Visual Modeler is view-only. dbdiagram.io has a code panel alongside. DrawSQL has a sidebar for details.

**Risks:**
- Popover size limits for complex configs (ENUM with 20+ values)
- Scalability at 30+ definitions (cognitive overload)
- Mobile/tablet unusable
- Accessibility challenges (screen readers + React Flow)
- Discoverability (double-click = edit is not self-evident)

**Mitigation:** Detail Page / List View as fallback is marked as "deferred" in scope. Should not be deferred too long. Consider a toggleable inspect panel as escape hatch.

### Auto-Dependency Management (Graph-Driven)

**Status:** Not used in CMS tools. Closest analogy: Terraform (implicit dependencies from resource references).

**Risks:**
- Auto-create is intuitive, auto-delete was risky → **mitigated with confirmation dialog** (added to design doc)
- Multi-user race conditions: User A removes last ghost → dependency deleted. User B just created a reference to same app → inconsistency. Needs optimistic locking.

**Decision:** Auto-create silent, auto-delete with confirmation dialog.

### Join Table for All Relation Types

**Industry standard:** Inline FK for M2O/O2O, separate junction table for M2M (Strapi, Directus, Payload).

**Our approach:** Unified `content_reference` table for all types.

**Trade-off:** Slightly worse performance for O2O/M2O (extra JOIN vs. inline FK lookup), but unified query logic and perfect fit for EAV system. Acceptable for target audience.

**Recommendation:** Performance benchmarks with realistic data volumes (100k+ entries, 10+ relations) should be done early.

### Undo/Redo via Server-Side Revision Stack

**Industry comparison:** No CMS has schema-level undo. Figma/Google Docs use client-side CRDT/OT with async sync.

**Risks:**
- Latency: every Ctrl+Z is an API call. May feel slow.
- Multi-user: scoped to user session, but reverting a change that another user built upon is problematic.

**Recommendation:** Acceptable for MVP. Long-term: optimistic UI with client-side undo queue, async server sync.

### MIRROR Field UX

**Risk:** A field on "my" definition that is owned by another app is not intuitive. "Why can't I delete this field?" needs clear visual communication.

**Mitigation:** Visual distinction (italic, "mirror" icon, "Owned by: App X" badge). Deleting a MIRROR field deletes the source REFERENCE field (bidirectional delete).

## 5. Feature Roadmap Recommendations

Based on this analysis, recommended follow-up phases after Content Definition:

| Priority | Feature | Reason |
|----------|---------|--------|
| High | Rich Text field type | Essential for any content platform |
| High | File/Media field type | Essential, S3/MinIO infrastructure exists |
| High | Content Management (Entries CRUD) | Next planned phase |
| Medium | Array/List field type | Repeatable structures without definition explosion |
| Medium | Component/Group field type | Reusable nested structures |
| Medium | Detail Page / List View fallback | Canvas-only scalability escape hatch |
| Medium | Field-Level Permissions | Enterprise requirement |
| Low | Computed/Lookup/Rollup fields | Nice-to-have for data navigation |
| Low | Conditional Field Visibility | Relevant when content entry forms exist |
| Low | Dynamic Zones/Blocks | Page-building use case |

## 6. Conclusion

Our design makes strong, defensible architectural choices (App-scoping, MIRROR ownership, ReBAC, unified join table, revision-based undo). The main gap is **field type coverage** — 8 types is thin for a production platform. The canvas-only approach is ambitious and unproven at scale, but the deferred detail page provides an escape hatch. The auto-dependency management with confirmation dialog is a good balance between convenience and safety.

The MIRROR field with `ownerAppId` is genuinely novel and solves a real problem. No other platform in this analysis has an equivalent concept for cross-package bidirectional relations without circular dependencies.
