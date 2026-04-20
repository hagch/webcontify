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
}

export interface NavSection {
  label: string;
  items: NavItem[];
}

export function getPlatformNavItems(): NavSection[] {
  return [
    {
      label: "Management",
      items: [
        { label: "Tenants", href: "/tenants", icon: Building2 },
        { label: "Platform Admins", href: "/admins", icon: ShieldCheck },
      ],
    },
    {
      label: "System",
      items: [
        { label: "Settings", href: "/settings", icon: Settings },
      ],
    },
  ];
}

export function getWorkspaceNavItems(options: {
  isAdmin: boolean;
}): NavSection[] {
  const sections: NavSection[] = [
    {
      label: "Overview",
      items: [
        { label: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
        { label: "Apps", href: "/apps", icon: AppWindow },
      ],
    },
  ];

  if (options.isAdmin) {
    sections.push({
      label: "Management",
      items: [
        { label: "Users", href: "/users", icon: Users },
        { label: "Roles", href: "/roles", icon: Shield },
      ],
    });

    sections.push({
      label: "Settings",
      items: [
        { label: "Branding", href: "/settings/branding", icon: Palette },
        { label: "Email", href: "/settings/email", icon: Mail },
        { label: "Domain", href: "/settings/domain", icon: Globe },
      ],
    });
  }

  return sections;
}
