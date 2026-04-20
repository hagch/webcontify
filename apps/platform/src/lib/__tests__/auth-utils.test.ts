import { describe, it, expect } from "vitest";
import {
  getKeycloakIssuer,
  getRealmFromHostname,
  isPlatformHost,
  shouldUsePlatformSession,
} from "../auth-utils";

describe("auth-utils", () => {
  it("should return platform realm for platform.localhost", () => {
    expect(getRealmFromHostname("platform.localhost")).toBe(
      "webcontify-platform"
    );
  });

  it("should return tenant realm for tenant subdomain", () => {
    expect(getRealmFromHostname("acme.localhost")).toBe("tenant-acme");
  });

  it("should build correct issuer URL for platform", () => {
    expect(getKeycloakIssuer("platform.localhost")).toBe(
      "http://auth.localhost/realms/webcontify-platform"
    );
  });

  it("should build correct issuer URL for tenant", () => {
    expect(getKeycloakIssuer("acme.localhost")).toBe(
      "http://auth.localhost/realms/tenant-acme"
    );
  });

  it("should identify platform host", () => {
    expect(isPlatformHost("platform.localhost")).toBe(true);
    expect(isPlatformHost("acme.localhost")).toBe(false);
  });

  it("should use platform session for platform admin on tenant subdomain", () => {
    expect(shouldUsePlatformSession(false, true)).toBe(true);
  });

  it("should not use platform session for non-admin on tenant subdomain", () => {
    expect(shouldUsePlatformSession(false, false)).toBe(false);
  });

  it("should always use platform session on platform host", () => {
    expect(shouldUsePlatformSession(true, false)).toBe(true);
  });
});
