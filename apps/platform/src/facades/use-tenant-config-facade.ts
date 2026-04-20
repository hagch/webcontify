"use client";

import { useQueryClient } from "@tanstack/react-query";
import {
  useGetAdminTenantConfig,
  useUpdateAdminBrandingConfig,
  useUpdateAdminEmailConfig,
  useUpdateAdminDomainConfig,
  useTestAdminEmailConfig,
  getGetAdminTenantConfigQueryKey,
  type UpdateBrandingConfigRequest,
  type UpdateEmailConfigRequest,
  type UpdateDomainConfigRequest,
  type TestEmailRequest,
} from "@/generated/tenant";

export function useTenantConfigFacade(tenantId: string) {
  const queryClient = useQueryClient();
  const configQuery = useGetAdminTenantConfig(tenantId);

  const invalidateConfig = () => {
    queryClient.invalidateQueries({
      queryKey: getGetAdminTenantConfigQueryKey(tenantId),
    });
  };

  const brandingMutation = useUpdateAdminBrandingConfig({
    mutation: { onSuccess: invalidateConfig },
  });
  const emailMutation = useUpdateAdminEmailConfig({
    mutation: { onSuccess: invalidateConfig },
  });
  const domainMutation = useUpdateAdminDomainConfig({
    mutation: { onSuccess: invalidateConfig },
  });
  const testEmailMutation = useTestAdminEmailConfig();

  const config =
    configQuery.data?.status === 200 ? configQuery.data.data : undefined;

  return {
    config,
    isLoading: configQuery.isLoading,
    error: configQuery.error,

    updateBranding: (data: UpdateBrandingConfigRequest) =>
      brandingMutation.mutateAsync({ tenantId, data }),
    isUpdatingBranding: brandingMutation.isPending,

    updateEmail: (data: UpdateEmailConfigRequest) =>
      emailMutation.mutateAsync({ tenantId, data }),
    isUpdatingEmail: emailMutation.isPending,

    updateDomain: (data: UpdateDomainConfigRequest) =>
      domainMutation.mutateAsync({ tenantId, data }),
    isUpdatingDomain: domainMutation.isPending,

    testEmail: (data: TestEmailRequest) =>
      testEmailMutation.mutateAsync({ tenantId, data }),
    isTestingEmail: testEmailMutation.isPending,
  };
}
