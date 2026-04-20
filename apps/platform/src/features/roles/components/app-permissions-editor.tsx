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

const APP_RESOURCE_ACTIONS: Record<string, string[]> = {
  app_management: ["viewer", "editor", "admin"],
};

type PermissionState = Record<string, Record<string, boolean>>;

function buildInitialState(
  permissions: RolePermissionPreviewResponse[]
): PermissionState {
  const state: PermissionState = {};
  for (const resourceType of Object.keys(APP_RESOURCE_ACTIONS)) {
    state[resourceType] = {};
    const existing = permissions.find((p) => p.resourceType === resourceType);
    for (const action of APP_RESOURCE_ACTIONS[resourceType]) {
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

interface AppPermissionsEditorProps {
  roleId: string;
  permissions: RolePermissionPreviewResponse[];
  allPermissions: RolePermissionPreviewResponse[];
  isSystemRole: boolean;
}

export function AppPermissionsEditor({
  roleId,
  permissions,
  allPermissions,
  isSystemRole,
}: AppPermissionsEditorProps) {
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
      // viewer is implied when any higher action is set
      const otherActions = APP_RESOURCE_ACTIONS[resourceType].filter(
        (a) => a !== "viewer"
      );
      const anyOtherChecked = otherActions.some(
        (a) => (a === action ? checked : updated[resourceType][a])
      );
      if (anyOtherChecked) {
        updated[resourceType]["viewer"] = true;
      }
      return updated;
    });
  }

  function handleSave() {
    // Merge app permissions with existing non-app permissions
    const appPermissions = Object.entries(state)
      .map(([resourceType, actions]) => ({
        resourceType,
        actions: Object.entries(actions)
          .filter(([, checked]) => checked)
          .map(([action]) => action),
      }))
      .filter((p) => p.actions.length > 0);

    const nonAppPermissions = allPermissions
      .filter((p) => !APP_RESOURCE_ACTIONS[p.resourceType])
      .map((p) => ({
        resourceType: p.resourceType,
        actions: [...p.actions],
      }));

    const mergedPermissions = [...nonAppPermissions, ...appPermissions];

    mutate(
      { roleId, data: { permissions: mergedPermissions } },
      {
        onSuccess: () => {
          toast.success(t("permissions.saveSuccess"));
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

  function getActionLabel(action: string): string {
    const key = `permissions.actions.${action}` as const;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return t(key as any);
  }

  function isViewerImplied(resourceType: string): boolean {
    const otherActions = APP_RESOURCE_ACTIONS[resourceType].filter(
      (a) => a !== "viewer"
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

        <Card>
          <CardHeader className="pb-2 pt-4">
            <CardTitle className="text-sm font-medium">
              {t("permissions.appManagement.global")}
            </CardTitle>
            <p className="text-xs text-muted-foreground">
              {t("permissions.appManagement.globalDescription")}
            </p>
          </CardHeader>
          <CardContent className="pb-4">
            <div className="space-y-2">
              {APP_RESOURCE_ACTIONS["app_management"].map((action) => {
                const isViewer = action === "viewer";
                const viewerImplied =
                  isViewer && isViewerImplied("app_management");
                const userHasPermission = can("app_management", action);
                const isDisabled =
                  isSystemRole || !canEditPermissions || viewerImplied || !userHasPermission;
                const isChecked =
                  state["app_management"]?.[action] ?? false;
                const checkboxId = `perm-app_management-${action}`;

                const checkbox = (
                  <div className="flex items-center gap-2">
                    <Checkbox
                      id={checkboxId}
                      data-testid={`permission-app_management-${action}`}
                      checked={isChecked}
                      disabled={isDisabled}
                      onCheckedChange={(checked) => {
                        if (!isDisabled) {
                          handleCheckboxChange(
                            "app_management",
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

                if (!isSystemRole && !viewerImplied && !userHasPermission) {
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

        {!isSystemRole && canEditPermissions && (
          <div className="flex items-center gap-2">
            {isDirty && (
              <Button
                data-testid="cancel-app-permissions-button"
                variant="outline"
                onClick={handleCancel}
                disabled={isPending}
              >
                {t("permissions.cancel")}
              </Button>
            )}
            {isDirty && (
              <Button
                data-testid="save-app-permissions-button"
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
