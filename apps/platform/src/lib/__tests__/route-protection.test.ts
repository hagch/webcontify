import { describe, it, expect } from "vitest";
import { isProtectedPath, isPublicPath } from "../route-protection";

describe("route-protection", () => {
  describe("isPublicPath", () => {
    it("returns true for auth API routes", () => {
      expect(isPublicPath("/api/auth/callback")).toBe(true);
      expect(isPublicPath("/api/auth/signin")).toBe(true);
    });

    it("returns true for signin page", () => {
      expect(isPublicPath("/signin")).toBe(true);
    });

    it("returns true for static assets", () => {
      expect(isPublicPath("/_next/static/chunk.js")).toBe(true);
      expect(isPublicPath("/favicon.ico")).toBe(true);
    });

    it("returns false for protected routes", () => {
      expect(isPublicPath("/tenants")).toBe(false);
      expect(isPublicPath("/dashboard")).toBe(false);
    });
  });

  describe("isProtectedPath", () => {
    it("returns true for platform routes", () => {
      expect(isProtectedPath("/tenants")).toBe(true);
      expect(isProtectedPath("/tenants/123")).toBe(true);
      expect(isProtectedPath("/admins")).toBe(true);
    });

    it("returns true for workspace routes", () => {
      expect(isProtectedPath("/dashboard")).toBe(true);
      expect(isProtectedPath("/apps")).toBe(true);
      expect(isProtectedPath("/apps/my-app/content")).toBe(true);
      expect(isProtectedPath("/users")).toBe(true);
      expect(isProtectedPath("/roles")).toBe(true);
      expect(isProtectedPath("/settings/branding")).toBe(true);
      expect(isProtectedPath("/settings/email")).toBe(true);
      expect(isProtectedPath("/settings/domain")).toBe(true);
    });

    it("returns false for public routes", () => {
      expect(isProtectedPath("/signin")).toBe(false);
      expect(isProtectedPath("/api/auth/callback")).toBe(false);
    });
  });
});
