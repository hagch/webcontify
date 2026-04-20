import { auth } from "@/auth";
import { NextRequest, NextResponse } from "next/server";
import { isProtectedPath, isPublicPath } from "@/lib/route-protection";
import { isPlatformHost } from "@/lib/auth-utils";

export async function middleware(request: NextRequest) {
  const session = await auth();
  const { pathname } = request.nextUrl;
  const hostname =
    request.headers.get("host")?.split(":")[0] ?? "platform.localhost";

  if (isPublicPath(pathname)) {
    return NextResponse.next();
  }

  if (pathname === "/") {
    if (isPlatformHost(hostname)) {
      return NextResponse.redirect(new URL("/tenants", request.url));
    }
    return NextResponse.redirect(new URL("/dashboard", request.url));
  }

  if (isProtectedPath(pathname) && !session) {
    const signInUrl = new URL("/signin", request.url);
    signInUrl.searchParams.set("callbackUrl", request.url);
    return NextResponse.redirect(signInUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
