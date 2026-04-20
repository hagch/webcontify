"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { useTranslations } from "next-intl";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  SidebarSeparator,
} from "@/components/ui/sidebar";
import { NavUser } from "./nav-user";
import { AppVersionSelector } from "./app-version-selector";
import type { AppNavSection } from "./app-nav-config";
import type { AppVersionResponse } from "@/generated/app";

interface AppSidebarNavProps {
  appName: string;
  appSlug: string;
  sections: AppNavSection[];
  selectedVersion?: string;
  versions?: AppVersionResponse[];
  hasDraft?: boolean;
  hasUnpublishedChanges?: boolean;
  unpublishedChangeCount?: number;
  onVersionSelect?: (versionId: string) => void;
}

export function AppSidebarNav({
  appName,
  appSlug,
  sections,
  selectedVersion,
  versions,
  hasDraft,
  hasUnpublishedChanges,
  unpublishedChangeCount,
  onVersionSelect,
}: AppSidebarNavProps) {
  const pathname = usePathname();
  const t = useTranslations("nav");

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="sm"
              render={<Link href="/apps" />}
              tooltip={t("appSidebar.backToApps")}
            >
              <ChevronLeft />
              <span>{t("appSidebar.backToApps")}</span>
            </SidebarMenuButton>
          </SidebarMenuItem>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" render={<Link href={`/apps/${appSlug}/definitions`} />}>
              <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <span className="text-sm font-bold">{appName.charAt(0).toUpperCase()}</span>
              </div>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-semibold">{appName}</span>
              </div>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        {selectedVersion && versions && onVersionSelect && (
          <AppVersionSelector
            selectedVersion={selectedVersion}
            versions={versions}
            hasDraft={hasDraft ?? false}
            hasUnpublishedChanges={hasUnpublishedChanges ?? false}
            unpublishedChangeCount={unpublishedChangeCount ?? 0}
            onSelect={onVersionSelect}
          />
        )}
      </SidebarHeader>
      <SidebarContent>
        {sections.map((section, index) => (
          <div key={section.label}>
            {index > 0 && <SidebarSeparator />}
            <SidebarGroup>
              <SidebarGroupLabel>{section.label}</SidebarGroupLabel>
              {section.disabledHint && section.items.length === 0 ? (
                <p className="px-2 py-1 text-xs text-muted-foreground">
                  {section.disabledHint}
                </p>
              ) : section.dataHint && section.items.length === 0 ? (
                <button
                  type="button"
                  className="w-full px-2 py-1 text-left text-xs text-muted-foreground hover:text-foreground transition-colors"
                  onClick={() => onVersionSelect?.("draft")}
                >
                  {section.dataHint}
                </button>
              ) : (
                <SidebarMenu>
                  {section.items.map((item) => {
                    const isActive = pathname === item.href || pathname.startsWith(item.href + "/");
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
                        {item.badge && (
                          <SidebarMenuBadge>{item.badge}</SidebarMenuBadge>
                        )}
                      </SidebarMenuItem>
                    );
                  })}
                </SidebarMenu>
              )}
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
