"use client";

import { signOut, useSession } from "next-auth/react";
import { useTranslations } from "next-intl";
import { ChevronsUpDown, KeyRound, LogOut, Moon, Sun, Monitor, User } from "lucide-react";
import { useGetAccountUrl, useGetMyProfile, useGetAvatar } from "@/generated/auth";
import { useTheme } from "next-themes";
import { useRouter } from "next/navigation";
import { useAuth } from "@/hooks/use-auth";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";

export function NavUser() {
  const { data: session } = useSession();
  const { user } = useAuth();
  const { setTheme } = useTheme();
  const { isMobile, open } = useSidebar();
  const { data: accountUrlData } = useGetAccountUrl();
  const { data: profileData } = useGetMyProfile();
  const accountUrl = accountUrlData?.status === 200 ? accountUrlData.data.url : undefined;
  const profile = profileData?.status === 200 ? profileData.data : undefined;
  const { data: avatarData } = useGetAvatar({ query: { enabled: !!profile?.hasAvatar } });
  const avatarUrl = avatarData?.status === 200 ? avatarData.data.avatarUrl : undefined;
  const t = useTranslations("nav");
  const router = useRouter();

  // Prefer profile name from backend (up-to-date) over session name (from JWT, may be stale)
  const displayName = profile
    ? `${profile.firstName} ${profile.lastName}`
    : user?.name ?? "User";
  const displayEmail = profile?.email ?? user?.email ?? "";

  const initials = displayName
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger
            render={
              <SidebarMenuButton
                data-testid="nav-user-menu"
                size="lg"
                className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
              />
            }
          >
            <Avatar className="h-8 w-8 rounded-lg">
              {avatarUrl && (
                <AvatarImage src={avatarUrl} alt={initials} />
              )}
              <AvatarFallback className="rounded-lg">
                {initials}
              </AvatarFallback>
            </Avatar>
            <div className="grid flex-1 text-left text-sm leading-tight">
              <span className="truncate font-semibold">
                {displayName}
              </span>
              <span className="truncate text-xs text-muted-foreground">
                {displayEmail}
              </span>
            </div>
            <ChevronsUpDown className="ml-auto size-4" />
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-56 rounded-lg"
            side={isMobile ? "bottom" : "right"}
            align="end"
            sideOffset={4}
          >
            <DropdownMenuGroup>
              {!open && (
                <>
                  <DropdownMenuLabel className="p-0 font-normal">
                    <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
                      <Avatar className="h-8 w-8 rounded-lg">
                        {profile?.hasAvatar && (
                          <AvatarImage src="/api/v1/auth/profile/avatar" alt={initials} />
                        )}
                        <AvatarFallback className="rounded-lg">
                          {initials}
                        </AvatarFallback>
                      </Avatar>
                      <div className="grid flex-1 text-left text-sm leading-tight">
                        <span className="truncate font-semibold">
                          {displayName}
                        </span>
                        <span className="truncate text-xs text-muted-foreground">
                          {displayEmail}
                        </span>
                      </div>
                    </div>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                </>
              )}

              <DropdownMenuItem
                data-testid="nav-profile-link"
                className="cursor-pointer"
                onClick={() => router.push("/profile")}
              >
                <User className="mr-2 size-4" />
                {t("profile")}
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuSub>
                <DropdownMenuSubTrigger data-testid="nav-theme-submenu">
                  <Sun className="mr-2 size-4 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
                  <Moon className="absolute mr-2 size-4 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
                  {t("theme")}
                </DropdownMenuSubTrigger>
                <DropdownMenuSubContent>
                  <DropdownMenuItem
                    data-testid="nav-theme-light"
                    onClick={() => setTheme("light")}
                  >
                    <Sun className="mr-2 size-4" />
                    {t("themeLight")}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    data-testid="nav-theme-dark"
                    onClick={() => setTheme("dark")}
                  >
                    <Moon className="mr-2 size-4" />
                    {t("themeDark")}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    data-testid="nav-theme-system"
                    onClick={() => setTheme("system")}
                  >
                    <Monitor className="mr-2 size-4" />
                    {t("themeSystem")}
                  </DropdownMenuItem>
                </DropdownMenuSubContent>
              </DropdownMenuSub>
              {accountUrl && (
                <>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    data-testid="password-change-link"
                    onClick={() => window.open(accountUrl, "_blank")}
                  >
                    <KeyRound className="mr-2 size-4" />
                    {t("changePassword")}
                  </DropdownMenuItem>
                </>
              )}
              <DropdownMenuSeparator />
              <DropdownMenuItem
                data-testid="nav-sign-out"
                onClick={async () => {
                  const idToken = session?.idToken;
                  const issuer = session?.issuer;
                  // Clear NextAuth session first
                  await signOut({ redirect: false });
                  // Then end Keycloak session via browser redirect
                  if (idToken && issuer) {
                    const logoutUrl = new URL(`${issuer}/protocol/openid-connect/logout`);
                    logoutUrl.searchParams.set("id_token_hint", idToken);
                    logoutUrl.searchParams.set("post_logout_redirect_uri", `${window.location.origin}/signin`);
                    window.location.href = logoutUrl.toString();
                  } else {
                    window.location.href = "/signin";
                  }
                }}
              >
                <LogOut className="mr-2 size-4" />
                {t("signOut")}
              </DropdownMenuItem>
            </DropdownMenuGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
