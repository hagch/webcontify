import { handlers } from "@/auth";
import { NextRequest } from "next/server";

/**
 * Workaround: next-auth@5.0.0-beta.30 redirect_uri Mismatch hinter Reverse Proxy.
 * signIn-Action nutzt createActionURL (x-forwarded-host), Route Handler nutzt rohe req.url.
 * TODO: Pruefen ob ab next-auth >= 5.0.0 stable dieser Workaround entfallen kann.
 */
function withForwardedUrl(
  handler: (req: NextRequest) => Promise<Response>
): (req: NextRequest) => Promise<Response> {
  return async (req: NextRequest) => {
    const forwardedHost = req.headers.get("x-forwarded-host");
    if (!forwardedHost) return handler(req);

    const proto = req.headers.get("x-forwarded-proto") ?? "http";
    const { pathname, search } = req.nextUrl;
    const correctedUrl = `${proto}://${forwardedHost}${pathname}${search}`;

    return handler(new NextRequest(correctedUrl, req));
  };
}

export const GET = withForwardedUrl(handlers.GET);
export const POST = withForwardedUrl(handlers.POST);
