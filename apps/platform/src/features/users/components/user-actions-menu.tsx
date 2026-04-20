"use client";

import { useTranslations } from "next-intl";
import { MoreHorizontal, Mail, UserCheck, UserX, Trash2, UserPlus } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { UserStatus, type UserResponse } from "@/generated/identity";
import { useUserActions } from "../hooks/use-user-actions";
import { usePermissions } from "@/hooks/use-permissions";

interface UserActionsMenuProps {
  user: UserResponse;
  onDeleteRequest?: (user: UserResponse) => void;
  onCloneRolesRequest?: (user: UserResponse) => void;
}

export function UserActionsMenu({
  user,
  onDeleteRequest,
  onCloneRolesRequest,
}: UserActionsMenuProps) {
  const { resendInvite, activateUser, deactivateUser, isUpdatingStatus, isResendingInvite } =
    useUserActions();
  const { can } = usePermissions();
  const t = useTranslations("users");

  const canEdit = can("user_management", "editor");
  const canInvite = can("user_management", "inviter");
  const canDelete = can("user_management", "deleter");

  const fullName = `${user.firstName} ${user.lastName}`;

  if (!canEdit && !canInvite && !canDelete) {
    return null;
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            data-testid={`actions-menu-${user.id}`}
            variant="ghost"
            size="icon"
            aria-label={t("actionsMenu.label", { name: fullName })}
          />
        }
      >
        <MoreHorizontal className="size-4" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {canEdit && user.status === UserStatus.INVITED && (
          <DropdownMenuItem
            disabled={isResendingInvite}
            onClick={() => resendInvite(user.id)}
          >
            <Mail className="mr-2 size-4" />
            {t("actionsMenu.resendInvite")}
          </DropdownMenuItem>
        )}
        {canEdit && (user.status === UserStatus.ACTIVE || user.status === UserStatus.INVITED) && (
          <DropdownMenuItem
            disabled={isUpdatingStatus}
            onClick={() => deactivateUser(user.id)}
          >
            <UserX className="mr-2 size-4" />
            {t("actionsMenu.deactivate")}
          </DropdownMenuItem>
        )}
        {canEdit && user.status === UserStatus.DEACTIVATED && (
          <DropdownMenuItem
            disabled={isUpdatingStatus}
            onClick={() => activateUser(user.id)}
          >
            <UserCheck className="mr-2 size-4" />
            {t("actionsMenu.activate")}
          </DropdownMenuItem>
        )}
        {canInvite && user.status === UserStatus.ACTIVE && (
          <DropdownMenuItem onClick={() => onCloneRolesRequest?.(user)}>
            <UserPlus className="mr-2 size-4" />
            {t("actionsMenu.cloneRoles")}
          </DropdownMenuItem>
        )}
        {canDelete && <DropdownMenuSeparator />}
        {canDelete && (
          <DropdownMenuItem
            variant="destructive"
            onClick={() => onDeleteRequest?.(user)}
          >
            <Trash2 className="mr-2 size-4" />
            {t("actionsMenu.delete")}
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
