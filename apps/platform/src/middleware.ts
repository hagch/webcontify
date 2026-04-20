import { auth } from "@/auth";
import { NextRequest, NextResponse } from "next/server";
import { isProtectedPath, isPublicPath } from "@/lib/route-protection";
import { isPlatformHost } from "@/lib/auth-utils";

function getExternalUrl(request: NextRequest): URL {
  const forwardedHost = request.headers.get("x-forwarded-host");
  const forwardedProto = request.headers.get("x-forwarded-proto") ?? "http";
  if (forwardedHost) {
    return new URL(
      `${forwardedProto}://${forwardedHost}${request.nextUrl.pathname}${request.nextUrl.search}`
    );
  }
  return request.nextUrl;
}

export async function middleware(request: NextRequest) {
  const session = await auth();
  const { pathname } = request.nextUrl;
  const hostname =
    (request.headers.get("x-forwarded-host") ?? request.headers.get("host"))
      ?.split(":")[0] ?? "platform.localhost";

  if (isPublicPath(pathname)) {
    return NextResponse.next();
  }

  if (pathname === "/") {
    if (isPlatformHost(hostname)) {
      return NextResponse.redirect(new URL("/tenants", getExternalUrl(request)));
    }
    return NextResponse.redirect(new URL("/dashboard", getExternalUrl(request)));
  }

  if (isProtectedPath(pathname) && (!session || session.error === "RefreshAccessTokenError")) {
    const externalUrl = getExternalUrl(request);
    const signInUrl = new URL("/signin", externalUrl);
    signInUrl.searchParams.set("callbackUrl", externalUrl.toString());
    return NextResponse.redirect(signInUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
