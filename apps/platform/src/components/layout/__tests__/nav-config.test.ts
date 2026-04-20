import { describe, it, expect } from "vitest";
import {
  getPlatformNavItems,
  getWorkspaceNavItems,
} from "../nav-config";

describe("nav-config", () => {
  describe("getPlatformNavItems", () => {
    it("returns platform admin sections", () => {
      const sections = getPlatformNavItems();
      expect(sections.length).toBeGreaterThan(0);

      const firstSection = sections[0];
      expect(firstSection.label).toBeDefined();
      expect(firstSection.items.length).toBeGreaterThan(0);
    });

    it("includes tenants and admins nav items", () => {
      const sections = getPlatformNavItems();
      const allItems = sections.flatMap((s) => s.items);

      expect(allItems.find((i) => i.href === "/tenants")).toBeDefined();
      expect(allItems.find((i) => i.href === "/admins")).toBeDefined();
    });

    it("includes settings nav item", () => {
      const sections = getPlatformNavItems();
      const allItems = sections.flatMap((s) => s.items);

      expect(allItems.find((i) => i.href === "/settings")).toBeDefined();
    });
  });

  describe("getWorkspaceNavItems", () => {
    it("returns workspace sections for admin role", () => {
      const sections = getWorkspaceNavItems({ isAdmin: true });
      const allItems = sections.flatMap((s) => s.items);

      expect(allItems.find((i) => i.href === "/dashboard")).toBeDefined();
      expect(allItems.find((i) => i.href === "/apps")).toBeDefined();
      expect(allItems.find((i) => i.href === "/users")).toBeDefined();
      expect(
        allItems.find((i) => i.href === "/settings/branding")
      ).toBeDefined();
    });

    it("returns limited workspace sections for user role", () => {
      const sections = getWorkspaceNavItems({ isAdmin: false });
      const allItems = sections.flatMap((s) => s.items);

      expect(allItems.find((i) => i.href === "/dashboard")).toBeDefined();
      expect(allItems.find((i) => i.href === "/apps")).toBeDefined();
      expect(allItems.find((i) => i.href === "/users")).toBeUndefined();
      expect(
        allItems.find((i) => i.href === "/settings/branding")
      ).toBeUndefined();
    });

    it("all items have icon and label", () => {
      const sections = getWorkspaceNavItems({ isAdmin: true });
      const allItems = sections.flatMap((s) => s.items);

      for (const item of allItems) {
        expect(item.label).toBeTruthy();
        expect(item.href).toBeTruthy();
        expect(item.icon).toBeDefined();
      }
    });
  });
});
