"use client";

import { useEffect, useRef } from "react";
import { useSession } from "next-auth/react";
import { setClientAccessToken } from "@/lib/orval-mutator";

export function AuthTokenSync({ children }: { children: React.ReactNode }) {
  const { data: session, status } = useSession();
  const tokenSet = useRef(false);

  if (status === "authenticated" && session?.accessToken && !tokenSet.current) {
    setClientAccessToken(session.accessToken);
    tokenSet.current = true;
  }

  useEffect(() => {
    setClientAccessToken(session?.accessToken ?? undefined);
    tokenSet.current = !!session?.accessToken;
  }, [session?.accessToken]);

  if (status === "loading") {
    return null;
  }

  return <>{children}</>;
}
