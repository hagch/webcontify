import { auth } from "@/auth";
import { NextResponse } from "next/server";
import { isProtectedPath, isPublicPath } from "@/lib/route-protection";
import { isPlatformHost } from "@/lib/auth-utils";

export default auth((req) => {
  const { pathname } = req.nextUrl;
  const hostname = req.headers.get("host")?.split(":")[0] ?? "platform.localhost";

  if (isPublicPath(pathname)) {
    return NextResponse.next();
  }

  // Auf unbekannten Subdomains ohne Session: workspace-not-found anzeigen
  // (ausser es ist bereits die workspace-not-found Seite selbst)
  if (
    !isPlatformHost(hostname) &&
    !req.auth &&
    pathname !== "/workspace-not-found"
  ) {
    return NextResponse.redirect(new URL("/workspace-not-found", req.url));
  }

  if (isProtectedPath(pathname) && !req.auth) {
    const signInUrl = new URL("/api/auth/signin", req.url);
    signInUrl.searchParams.set("callbackUrl", req.url);
    return NextResponse.redirect(signInUrl);
  }

  return NextResponse.next();
});

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
