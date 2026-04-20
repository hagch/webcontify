"use client";

import { useState, useCallback } from "react";
import { useTranslations } from "next-intl";
import { Info } from "lucide-react";
import { toast } from "sonner";
import { useQueryClient } from "@tanstack/react-query";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  useSetRolePermissions,
  getGetRoleQueryKey,
} from "@/generated/identity";
import type { RolePermissionPreviewResponse } from "@/generated/identity";
import { usePermissions } from "@/hooks/use-permissions";

const RESOURCE_ACTIONS: Record<string, string[]> = {
  user_management: ["inviter", "editor", "deleter", "reader"],
  role_management: ["creator", "editor", "deleter", "reader"],
};

type PermissionState = Record<string, Record<string, boolean>>;

function buildInitialState(
  permissions: RolePermissionPreviewResponse[]
): PermissionState {
  const state: PermissionState = {};
  for (const resourceType of Object.keys(RESOURCE_ACTIONS)) {
    state[resourceType] = {};
    const existing = permissions.find((p) => p.resourceType === resourceType);
    for (const action of RESOURCE_ACTIONS[resourceType]) {
      state[resourceType][action] = existing?.actions.includes(action) ?? false;
    }
  }
  return state;
}

function statesEqual(a: PermissionState, b: PermissionState): boolean {
  for (const resource of Object.keys(a)) {
    for (const action of Object.keys(a[resource])) {
      if (a[resource][action] !== b[resource]?.[action]) return false;
    }
  }
  return true;
}

interface RolePermissionsEditorProps {
  roleId: string;
  permissions: RolePermissionPreviewResponse[];
  isSystemRole: boolean;
}

export function RolePermissionsEditor({
  roleId,
  permissions,
  isSystemRole,
}: RolePermissionsEditorProps) {
  const t = useTranslations("roles");
  const queryClient = useQueryClient();
  const { can } = usePermissions();
  const canEditPermissions = can("role_management", "editor");
  const { mutate, isPending } = useSetRolePermissions();

  const [state, setState] = useState<PermissionState>(() =>
    buildInitialState(permissions)
  );
  const [savedState, setSavedState] = useState<PermissionState>(() =>
    buildInitialState(permissions)
  );

  const isDirty = !statesEqual(state, savedState);

  const handleCancel = useCallback(() => {
    setState(savedState);
  }, [savedState]);

  function handleCheckboxChange(
    resourceType: string,
    action: string,
    checked: boolean
  ) {
    setState((prev) => {
      const updated = {
        ...prev,
        [resourceType]: {
          ...prev[resourceType],
          [action]: checked,
        },
      };
      // reader wird impliziert, wenn irgendeine andere action gesetzt ist
      const otherActions = RESOURCE_ACTIONS[resourceType].filter(
        (a) => a !== "reader"
      );
      const anyOtherChecked = otherActions.some(
        (a) => (a === action ? checked : updated[resourceType][a])
      );
      if (anyOtherChecked) {
        updated[resourceType]["reader"] = true;
      }
      return updated;
    });
  }

  function handleSave() {
    const permissionsPayload = Object.entries(state)
      .map(([resourceType, actions]) => ({
        resourceType,
        actions: Object.entries(actions)
          .filter(([, checked]) => checked)
          .map(([action]) => action),
      }))
      .filter((p) => p.actions.length > 0);

    mutate(
      { roleId, data: { permissions: permissionsPayload } },
      {
        onSuccess: () => {
          toast.success(t("permissions.saveSuccess"));
          // Update savedState so the form is no longer dirty after a successful save
          setSavedState(state);
          queryClient.invalidateQueries({
            queryKey: getGetRoleQueryKey(roleId),
          });
        },
        onError: () => {
          toast.error(t("permissions.saveError"));
        },
      }
    );
  }

  function getResourceLabel(resourceType: string): string {
    const key = `permissions.resourceTypes.${resourceType}` as const;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return t(key as any);
  }

  function getActionLabel(action: string): string {
    const key = `permissions.actions.${action}` as const;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return t(key as any);
  }

  function isReaderImplied(resourceType: string): boolean {
    const otherActions = RESOURCE_ACTIONS[resourceType].filter(
      (a) => a !== "reader"
    );
    return otherActions.some((a) => state[resourceType]?.[a]);
  }

  return (
    <TooltipProvider>
      <div className="space-y-4">
        {isSystemRole ? (
          <div
            data-testid="system-role-permissions-notice"
            className="flex items-center gap-2 rounded-lg border border-stone-200 bg-stone-50 px-4 py-3 text-sm text-stone-600 dark:border-stone-800 dark:bg-stone-900/30 dark:text-stone-400"
          >
            <Info className="size-4 shrink-0" />
            <span>{t("permissions.systemRoleNotice")}</span>
          </div>
        ) : null}

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {Object.keys(RESOURCE_ACTIONS).map((resourceType) => {
            const actions = RESOURCE_ACTIONS[resourceType];
            return (
              <Card key={resourceType}>
                <CardHeader className="pb-2 pt-4">
                  <CardTitle className="text-sm font-medium">
                    {getResourceLabel(resourceType)}
                  </CardTitle>
                </CardHeader>
                <CardContent className="pb-4">
                  <div className="space-y-2">
                    {actions.map((action) => {
                      const isReader = action === "reader";
                      const readerImplied =
                        isReader && isReaderImplied(resourceType);
                      const userHasPermission = can(resourceType, action);
                      const isDisabled =
                        isSystemRole || !canEditPermissions || readerImplied || !userHasPermission;
                      const isChecked =
                        state[resourceType]?.[action] ?? false;
                      const checkboxId = `perm-${resourceType}-${action}`;

                      const checkbox = (
                        <div className="flex items-center gap-2">
                          <Checkbox
                            id={checkboxId}
                            data-testid={`permission-${resourceType}-${action}`}
                            checked={isChecked}
                            disabled={isDisabled}
                            onCheckedChange={(checked) => {
                              if (!isDisabled) {
                                handleCheckboxChange(
                                  resourceType,
                                  action,
                                  checked === true
                                );
                              }
                            }}
                          />
                          <Label
                            htmlFor={checkboxId}
                            className={
                              isDisabled
                                ? "cursor-not-allowed text-muted-foreground"
                                : "cursor-pointer"
                            }
                          >
                            {getActionLabel(action)}
                          </Label>
                        </div>
                      );

                      if (!isSystemRole && !readerImplied && !userHasPermission) {
                        return (
                          <Tooltip key={action}>
                            <TooltipTrigger>{checkbox}</TooltipTrigger>
                            <TooltipContent>
                              <p>{t("permissions.escalationTooltip")}</p>
                            </TooltipContent>
                          </Tooltip>
                        );
                      }

                      return <div key={action}>{checkbox}</div>;
                    })}
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>

        {!isSystemRole && canEditPermissions && (
          <div className="flex items-center gap-2">
            {isDirty && (
              <Button
                data-testid="cancel-permissions-button"
                variant="outline"
                onClick={handleCancel}
                disabled={isPending}
              >
                {t("permissions.cancel")}
              </Button>
            )}
            {isDirty && (
              <Button
                data-testid="save-permissions-button"
                onClick={handleSave}
                disabled={isPending}
              >
                {t("permissions.save")}
              </Button>
            )}
          </div>
        )}
      </div>
    </TooltipProvider>
  );
}
