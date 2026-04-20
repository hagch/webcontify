import {
  Building2,
  LayoutDashboard,
  AppWindow,
  Users,
  Shield,
  Settings,
  Palette,
  Mail,
  Globe,
  ShieldCheck,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  testId: string;
}

export interface NavSection {
  label: string;
  items: NavItem[];
}

export interface WorkspaceNavOptions {
  canAccessUserManagement: boolean;
  canAccessRoleManagement: boolean;
  canAccessTenantConfig: boolean;
}

type TranslationFn = (key: string) => string;

export function getPlatformNavItems(t: TranslationFn): NavSection[] {
  return [
    {
      label: t("sidebar.management"),
      items: [
        { label: t("sidebar.tenants"), href: "/tenants", icon: Building2, testId: "nav-item-tenants" },
        { label: t("sidebar.admins"), href: "/admins", icon: ShieldCheck, testId: "nav-item-admins" },
      ],
    },
    {
      label: t("sidebar.system"),
      items: [
        { label: t("sidebar.settings"), href: "/settings", icon: Settings, testId: "nav-item-settings" },
      ],
    },
  ];
}

export function getWorkspaceNavItems(
  options: WorkspaceNavOptions,
  t: TranslationFn
): NavSection[] {
  const sections: NavSection[] = [
    {
      label: t("sidebar.overview"),
      items: [
        { label: t("sidebar.dashboard"), href: "/dashboard", icon: LayoutDashboard, testId: "nav-item-dashboard" },
        { label: t("sidebar.apps"), href: "/apps", icon: AppWindow, testId: "nav-item-apps" },
      ],
    },
  ];

  const managementItems: NavItem[] = [];
  if (options.canAccessUserManagement) {
    managementItems.push({
      label: t("sidebar.users"),
      href: "/users",
      icon: Users,
      testId: "nav-item-users",
    });
  }
  if (options.canAccessRoleManagement) {
    managementItems.push({
      label: t("sidebar.roles"),
      href: "/roles",
      icon: Shield,
      testId: "nav-item-roles",
    });
  }
  if (managementItems.length > 0) {
    sections.push({ label: t("sidebar.management"), items: managementItems });
  }

  if (options.canAccessTenantConfig) {
    sections.push({
      label: t("sidebar.settings"),
      items: [
        { label: t("sidebar.branding"), href: "/settings/branding", icon: Palette, testId: "nav-item-branding" },
        { label: t("sidebar.email"), href: "/settings/email", icon: Mail, testId: "nav-item-email" },
        { label: t("sidebar.domain"), href: "/settings/domain", icon: Globe, testId: "nav-item-domain" },
      ],
    });
  }

  return sections;
}
