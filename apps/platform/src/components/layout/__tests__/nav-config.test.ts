import { describe, it, expect } from "vitest";
import {
  getPlatformNavItems,
  getWorkspaceNavItems,
} from "../nav-config";

const mockT = (key: string) => key;

describe("nav-config", () => {
  describe("getPlatformNavItems", () => {
    it("returns platform admin sections", () => {
      const sections = getPlatformNavItems(mockT);
      expect(sections.length).toBeGreaterThan(0);

      const firstSection = sections[0];
      expect(firstSection.label).toBeDefined();
      expect(firstSection.items.length).toBeGreaterThan(0);
    });

    it("includes tenants and admins nav items", () => {
      const sections = getPlatformNavItems(mockT);
      const allItems = sections.flatMap((s) => s.items);

      expect(allItems.find((i) => i.href === "/tenants")).toBeDefined();
      expect(allItems.find((i) => i.href === "/admins")).toBeDefined();
    });

    it("includes settings nav item", () => {
      const sections = getPlatformNavItems(mockT);
      const allItems = sections.flatMap((s) => s.items);

      expect(allItems.find((i) => i.href === "/settings")).toBeDefined();
    });
  });

  describe("getWorkspaceNavItems", () => {
    it("shows only overview when no permissions", () => {
      const sections = getWorkspaceNavItems(
        {
          canAccessUserManagement: false,
          canAccessRoleManagement: false,
          canAccessTenantConfig: false,
        },
        mockT
      );
      expect(sections).toHaveLength(1);
      const allItems = sections.flatMap((s) => s.items);
      expect(allItems.find((i) => i.href === "/dashboard")).toBeDefined();
      expect(allItems.find((i) => i.href === "/apps")).toBeDefined();
    });

    it("shows management section when user has user_management access", () => {
      const sections = getWorkspaceNavItems(
        {
          canAccessUserManagement: true,
          canAccessRoleManagement: false,
          canAccessTenantConfig: false,
        },
        mockT
      );
      const mgmt = sections.find((s) => s.items.some((i) => i.href === "/users"));
      expect(mgmt).toBeDefined();
      expect(mgmt!.items).toHaveLength(1); // only Users, not Roles
    });

    it("shows management section with both items when both permissions present", () => {
      const sections = getWorkspaceNavItems(
        {
          canAccessUserManagement: true,
          canAccessRoleManagement: true,
          canAccessTenantConfig: false,
        },
        mockT
      );
      const mgmt = sections.find((s) => s.items.some((i) => i.href === "/users"));
      expect(mgmt!.items).toHaveLength(2);
    });

    it("shows settings section when user has tenant_config access", () => {
      const sections = getWorkspaceNavItems(
        {
          canAccessUserManagement: false,
          canAccessRoleManagement: false,
          canAccessTenantConfig: true,
        },
        mockT
      );
      const settings = sections.find((s) =>
        s.items.some((i) => i.href === "/settings/branding")
      );
      expect(settings).toBeDefined();
    });

    it("shows all sections for full admin", () => {
      const sections = getWorkspaceNavItems(
        {
          canAccessUserManagement: true,
          canAccessRoleManagement: true,
          canAccessTenantConfig: true,
        },
        mockT
      );
      expect(sections).toHaveLength(3);
    });

    it("all items have icon, label and testId", () => {
      const sections = getWorkspaceNavItems(
        {
          canAccessUserManagement: true,
          canAccessRoleManagement: true,
          canAccessTenantConfig: true,
        },
        mockT
      );
      const allItems = sections.flatMap((s) => s.items);

      for (const item of allItems) {
        expect(item.label).toBeTruthy();
        expect(item.href).toBeTruthy();
        expect(item.icon).toBeDefined();
        expect(item.testId).toBeTruthy();
      }
    });

    it("testIds match expected values", () => {
      const sections = getWorkspaceNavItems(
        {
          canAccessUserManagement: true,
          canAccessRoleManagement: true,
          canAccessTenantConfig: true,
        },
        mockT
      );
      const allItems = sections.flatMap((s) => s.items);

      expect(allItems.find((i) => i.href === "/dashboard")?.testId).toBe("nav-item-dashboard");
      expect(allItems.find((i) => i.href === "/apps")?.testId).toBe("nav-item-apps");
      expect(allItems.find((i) => i.href === "/users")?.testId).toBe("nav-item-users");
      expect(allItems.find((i) => i.href === "/roles")?.testId).toBe("nav-item-roles");
      expect(allItems.find((i) => i.href === "/settings/branding")?.testId).toBe("nav-item-branding");
      expect(allItems.find((i) => i.href === "/settings/email")?.testId).toBe("nav-item-email");
      expect(allItems.find((i) => i.href === "/settings/domain")?.testId).toBe("nav-item-domain");
    });
  });
});
