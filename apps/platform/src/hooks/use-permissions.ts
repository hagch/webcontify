"use client";

import { useGetEffectivePermissions } from "@/generated/auth";

export function usePermissions() {
  const query = useGetEffectivePermissions(
    { scope: "navigation" },
    {
      query: { staleTime: 60 * 1000 },
    }
  );

  const responseData =
    query.data?.status === 200 ? query.data.data : undefined;
  const permissions = responseData?.permissions ?? {};
  const isTenantAdmin = responseData?.isTenantAdmin ?? false;

  function can(resource: string, action: string): boolean {
    return permissions[resource]?.includes(action) ?? false;
  }

  function canAccess(resource: string): boolean {
    return (permissions[resource]?.length ?? 0) > 0;
  }

  return {
    permissions,
    isTenantAdmin,
    isLoading: query.isLoading,
    can,
    canAccess,
  };
}
