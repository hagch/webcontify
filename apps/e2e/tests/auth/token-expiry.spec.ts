import { test, expect } from "../../helpers/fixtures";

const API_BASE = process.env.API_URL ?? "http://localhost:8080/api/v1";

test.describe("Token Expiry", () => {
  test("should reject API call with invalid JWT", async ({ request }) => {
    const response = await request.get(`${API_BASE}/tenant/users`, {
      headers: { Authorization: "Bearer invalid-token-here" },
    });

    expect(response.status()).toBe(401);
  });

  test("should reject API call with no auth header", async ({ request }) => {
    const response = await request.get(`${API_BASE}/tenant/users`);

    expect(response.status()).toBe(401);
  });
});
