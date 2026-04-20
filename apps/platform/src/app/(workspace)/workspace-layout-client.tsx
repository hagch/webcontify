"use client";

import { useTranslations } from "next-intl";
import { usePathname } from "next/navigation";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { getWorkspaceNavItems } from "@/components/layout/nav-config";
import { usePermissions } from "@/hooks/use-permissions";
import { BrandingProvider, type ServerTenantConfig } from "@/providers/branding-provider";

interface WorkspaceLayoutClientProps {
  children: React.ReactNode;
  initialConfig: ServerTenantConfig | null;
}

export function WorkspaceLayoutClient({ children, initialConfig }: WorkspaceLayoutClientProps) {
  const t = useTranslations("nav");
  const pathname = usePathname();
  const { canAccess, isLoading } = usePermissions();

  // App detail routes provide their own sidebar via app layout
  const isAppDetailRoute = /^\/apps\/[^/]+\/.+/.test(pathname);

  const sections = getWorkspaceNavItems(
    {
      canAccessUserManagement: canAccess("user_management"),
      canAccessRoleManagement: canAccess("role_management"),
      canAccessTenantConfig: canAccess("tenant_config"),
      canAccessApps: canAccess("app_management"),
    },
    t
  );

  return (
    <BrandingProvider initialConfig={initialConfig}>
      {isAppDetailRoute ? (
        <>{children}</>
      ) : (
        <SidebarProvider>
          <AppSidebar sections={sections} isLoading={isLoading} />
          <SidebarInset>
            <main id="main-content">{children}</main>
          </SidebarInset>
        </SidebarProvider>
      )}
    </BrandingProvider>
  );
}
