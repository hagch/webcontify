import { describe, it, expect } from "vitest";
import { getAppNavSections } from "../app-nav-config";

const t = (key: string) => key;

describe("getAppNavSections", () => {
  it("returns schema section with definitions and dependencies", () => {
    const sections = getAppNavSections({ appSlug: "shop", definitions: [], hasPublishedVersion: true }, t);
    const schema = sections.find((s) => s.label === "appSidebar.schema");
    expect(schema).toBeDefined();
    expect(schema!.items).toHaveLength(2);
    expect(schema!.items[0].href).toBe("/apps/shop/definitions");
    expect(schema!.items[1].href).toBe("/apps/shop/dependencies");
  });

  it("returns data section with definition sub-items when published", () => {
    const sections = getAppNavSections({
      appSlug: "shop",
      definitions: [
        { slug: "products", name: "Products", entryCount: 24 },
        { slug: "orders", name: "Orders", entryCount: 156 },
      ],
      hasPublishedVersion: true,
    }, t);
    const data = sections.find((s) => s.label === "appSidebar.data");
    expect(data).toBeDefined();
    expect(data!.items).toHaveLength(2);
    expect(data!.items[0].href).toBe("/apps/shop/data/products");
    expect(data!.items[0].label).toBe("Products");
    expect(data!.items[0].badge).toBe("24");
    expect(data!.items[1].href).toBe("/apps/shop/data/orders");
  });

  it("returns empty data section when not published", () => {
    const sections = getAppNavSections({
      appSlug: "shop",
      definitions: [],
      hasPublishedVersion: false,
    }, t);
    const data = sections.find((s) => s.label === "appSidebar.data");
    expect(data).toBeDefined();
    expect(data!.items).toHaveLength(0);
    expect(data!.disabledHint).toBe("appSidebar.publishFirst");
  });

  it("returns config section with overview, versions, revisions", () => {
    const sections = getAppNavSections({ appSlug: "shop", definitions: [], hasPublishedVersion: true }, t);
    const config = sections.find((s) => s.label === "appSidebar.config");
    expect(config).toBeDefined();
    expect(config!.items).toHaveLength(3);
    expect(config!.items[0].href).toBe("/apps/shop/overview");
    expect(config!.items[1].href).toBe("/apps/shop/versions");
    expect(config!.items[2].href).toBe("/apps/shop/revisions");
  });
});
