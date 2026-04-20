import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

const mockGETHandler = vi.fn();
const mockPOSTHandler = vi.fn();

vi.mock("@/auth", () => ({
  handlers: {
    GET: mockGETHandler,
    POST: mockPOSTHandler,
  },
}));

// Import after mock setup so GET/POST are wrapped with the mocked handlers
const { GET, POST } = await import("../route");

describe("withForwardedUrl", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGETHandler.mockResolvedValue(new Response("ok"));
    mockPOSTHandler.mockResolvedValue(new Response("ok"));
  });

  describe("GET handler", () => {
    it("passes request unchanged when x-forwarded-host is absent", async () => {
      const req = new NextRequest("http://localhost/api/auth/session");

      await GET(req);

      expect(mockGETHandler).toHaveBeenCalledOnce();
      const receivedReq: NextRequest = mockGETHandler.mock.calls[0][0];
      expect(receivedReq.url).toBe("http://localhost/api/auth/session");
    });

    it("rewrites URL when x-forwarded-host and x-forwarded-proto are present", async () => {
      const req = new NextRequest("http://localhost/api/auth/session", {
        headers: {
          "x-forwarded-host": "acme.example.com",
          "x-forwarded-proto": "https",
        },
      });

      await GET(req);

      expect(mockGETHandler).toHaveBeenCalledOnce();
      const receivedReq: NextRequest = mockGETHandler.mock.calls[0][0];
      expect(receivedReq.url).toBe("https://acme.example.com/api/auth/session");
    });

    it("defaults to http when x-forwarded-host is set but x-forwarded-proto is absent", async () => {
      const req = new NextRequest("https://localhost/api/auth/session", {
        headers: {
          "x-forwarded-host": "acme.example.com",
        },
      });

      await GET(req);

      expect(mockGETHandler).toHaveBeenCalledOnce();
      const receivedReq: NextRequest = mockGETHandler.mock.calls[0][0];
      expect(receivedReq.url).toBe("http://acme.example.com/api/auth/session");
    });

    it("preserves query string when rewriting URL", async () => {
      const req = new NextRequest(
        "http://localhost/api/auth/callback/keycloak?code=abc&state=xyz",
        {
          headers: {
            "x-forwarded-host": "acme.example.com",
            "x-forwarded-proto": "https",
          },
        }
      );

      await GET(req);

      const receivedReq: NextRequest = mockGETHandler.mock.calls[0][0];
      expect(receivedReq.url).toBe(
        "https://acme.example.com/api/auth/callback/keycloak?code=abc&state=xyz"
      );
    });
  });

  describe("POST handler", () => {
    it("passes request unchanged when x-forwarded-host is absent", async () => {
      const req = new NextRequest("http://localhost/api/auth/signin", {
        method: "POST",
      });

      await POST(req);

      expect(mockPOSTHandler).toHaveBeenCalledOnce();
      const receivedReq: NextRequest = mockPOSTHandler.mock.calls[0][0];
      expect(receivedReq.url).toBe("http://localhost/api/auth/signin");
    });

    it("rewrites URL when x-forwarded-host and x-forwarded-proto are present", async () => {
      const req = new NextRequest("http://localhost/api/auth/signin", {
        method: "POST",
        headers: {
          "x-forwarded-host": "acme.example.com",
          "x-forwarded-proto": "https",
        },
      });

      await POST(req);

      expect(mockPOSTHandler).toHaveBeenCalledOnce();
      const receivedReq: NextRequest = mockPOSTHandler.mock.calls[0][0];
      expect(receivedReq.url).toBe("https://acme.example.com/api/auth/signin");
    });

    it("defaults to http when x-forwarded-host is set but x-forwarded-proto is absent", async () => {
      const req = new NextRequest("https://localhost/api/auth/signin", {
        method: "POST",
        headers: {
          "x-forwarded-host": "acme.example.com",
        },
      });

      await POST(req);

      expect(mockPOSTHandler).toHaveBeenCalledOnce();
      const receivedReq: NextRequest = mockPOSTHandler.mock.calls[0][0];
      expect(receivedReq.url).toBe("http://acme.example.com/api/auth/signin");
    });
  });
});
