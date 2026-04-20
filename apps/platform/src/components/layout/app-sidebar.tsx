"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem, SidebarRail,
  SidebarSeparator,
} from "@/components/ui/sidebar";
import { useBranding } from "@/providers/branding-provider";
import { useGetFile } from "@/generated/tenant";
import { NavUser } from "./nav-user";
import type { NavSection } from "./nav-config";


interface AppSidebarProps {
  sections: NavSection[];
  headerSlot?: React.ReactNode;
  isLoading?: boolean;
}

export function AppSidebar({ sections, headerSlot, isLoading }: AppSidebarProps) {
  const pathname = usePathname();
  const branding = useBranding();

  const tenantName = branding.tenantName || "Webcontify";
  const tenantInitial = tenantName.charAt(0).toUpperCase();
  // Use server-resolved logo URL, fall back to client-side resolution
  const { data: logoFileData } = useGetFile(
    { storageKey: branding.logoStorageKey ?? "" },
    { query: { enabled: !!branding.logoStorageKey && !branding.resolvedLogoUrl } }
  );
  const logoUrl = branding.resolvedLogoUrl || (logoFileData?.status === 200 ? logoFileData.data.url : null);

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              render={<Link href="/" />}
            >
              {logoUrl ? (
                <div className="flex w-full items-center justify-center py-1">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={logoUrl}
                    alt={tenantName}
                    className="max-h-8 max-w-full object-contain"
                  />
                </div>
              ) : (
                <>
                  <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                    <span className="text-sm font-bold">{tenantInitial}</span>
                  </div>
                  <div className="grid flex-1 text-left text-sm leading-tight">
                    <span className="truncate font-semibold">{tenantName}</span>
                  </div>
                </>
              )}
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        {headerSlot}
      </SidebarHeader>
      <SidebarContent>
        {sections.map((section, index) => (
          <div key={section.label}>
            {index > 0 && <SidebarSeparator />}
            <SidebarGroup>
              <SidebarGroupLabel>{section.label}</SidebarGroupLabel>
              <SidebarMenu>
                {section.items.map((item) => {
                  const isActive = pathname.startsWith(item.href);
                  return (
                    <SidebarMenuItem key={item.href}>
                      <SidebarMenuButton
                        render={<Link href={item.href} data-testid={item.testId} />}
                        isActive={isActive}
                        tooltip={item.label}
                      >
                        <item.icon />
                        <span>{item.label}</span>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  );
                })}
              </SidebarMenu>
            </SidebarGroup>
          </div>
        ))}
      </SidebarContent>
      <SidebarFooter>
        <NavUser />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
