"use client";

import { useEffect } from "react";
import { useSession } from "next-auth/react";
import { setClientAccessToken } from "@/lib/orval-mutator";

export function AuthTokenSync() {
  const { data: session } = useSession();

  useEffect(() => {
    setClientAccessToken(session?.accessToken ?? undefined);
  }, [session?.accessToken]);

  return null;
}
