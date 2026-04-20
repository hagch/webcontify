"use client";

import { useQueryClient } from "@tanstack/react-query";
import {
  useListTenants,
  useCreateTenant,
  useGetTenant,
  useUpdateTenant,
  useUpdateTenantStatus,
  useDeleteTenant,
  getListTenantsQueryKey,
  getGetTenantQueryKey,
  type ListTenantsParams,
  type CreateTenantRequest,
  type UpdateTenantRequest,
  type TenantStatus,
} from "@/generated/tenant";

export function useTenantListFacade(params?: ListTenantsParams) {
  const queryClient = useQueryClient();
  const tenantsQuery = useListTenants(params);
  const createMutation = useCreateTenant({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getListTenantsQueryKey(params) });
      },
    },
  });

  const listData =
    tenantsQuery.data?.status === 200 ? tenantsQuery.data.data : undefined;

  return {
    tenants: listData?.items ?? [],
    pageInfo: listData?.pageInfo,
    isLoading: tenantsQuery.isLoading,
    error: tenantsQuery.error,
    createTenant: (data: CreateTenantRequest) => createMutation.mutateAsync({ data }),
    isCreating: createMutation.isPending,
  };
}

export function useTenantDetailFacade(tenantId: string) {
  const queryClient = useQueryClient();

  const tenantQuery = useGetTenant(tenantId);

  const updateMutation = useUpdateTenant({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getGetTenantQueryKey(tenantId) });
        queryClient.invalidateQueries({ queryKey: getListTenantsQueryKey() });
      },
    },
  });

  const statusMutation = useUpdateTenantStatus({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getGetTenantQueryKey(tenantId) });
        queryClient.invalidateQueries({ queryKey: getListTenantsQueryKey() });
      },
    },
  });

  const deleteMutation = useDeleteTenant({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getListTenantsQueryKey() });
      },
    },
  });

  const tenant =
    tenantQuery.data?.status === 200 ? tenantQuery.data.data : undefined;

  return {
    tenant,
    isLoading: tenantQuery.isLoading,
    error: tenantQuery.error,
    updateTenant: (data: UpdateTenantRequest) =>
      updateMutation.mutateAsync({ tenantId, data }),
    isUpdating: updateMutation.isPending,
    updateStatus: (status: TenantStatus) =>
      statusMutation.mutateAsync({ tenantId, data: { status } }),
    isUpdatingStatus: statusMutation.isPending,
    deleteTenant: () => deleteMutation.mutateAsync({ tenantId }),
    isDeleting: deleteMutation.isPending,
  };
}
