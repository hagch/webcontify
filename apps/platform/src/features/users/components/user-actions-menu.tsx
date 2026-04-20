"use client";

import { MoreHorizontal, Mail, UserCheck, UserX, Trash2 } from "lucide-react";
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

interface UserActionsMenuProps {
  user: UserResponse;
  onDeleteRequest?: (user: UserResponse) => void;
}

export function UserActionsMenu({
  user,
  onDeleteRequest,
}: UserActionsMenuProps) {
  const { resendInvite, activateUser, deactivateUser, isUpdatingStatus, isResendingInvite } =
    useUserActions();

  const fullName = `${user.firstName} ${user.lastName}`;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            variant="ghost"
            size="icon"
            aria-label={`Aktionen fuer ${fullName}`}
          />
        }
      >
        <MoreHorizontal className="size-4" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {user.status === UserStatus.INVITED && (
          <DropdownMenuItem
            disabled={isResendingInvite}
            onClick={() => resendInvite(user.id)}
          >
            <Mail className="mr-2 size-4" />
            Einladung erneut senden
          </DropdownMenuItem>
        )}
        {(user.status === UserStatus.ACTIVE || user.status === UserStatus.INVITED) && (
          <DropdownMenuItem
            disabled={isUpdatingStatus}
            onClick={() => deactivateUser(user.id)}
          >
            <UserX className="mr-2 size-4" />
            Deaktivieren
          </DropdownMenuItem>
        )}
        {user.status === UserStatus.DEACTIVATED && (
          <DropdownMenuItem
            disabled={isUpdatingStatus}
            onClick={() => activateUser(user.id)}
          >
            <UserCheck className="mr-2 size-4" />
            Aktivieren
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem
          variant="destructive"
          onClick={() => onDeleteRequest?.(user)}
        >
          <Trash2 className="mr-2 size-4" />
          Loeschen
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
