import {
  Grid3X3,
  GitBranch,
  Database,
  Info,
  History,
  FileStack,
  type LucideIcon,
} from "lucide-react";

export interface AppNavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  testId: string;
  badge?: string;
}

export interface AppNavSection {
  label: string;
  items: AppNavItem[];
  disabledHint?: string;
  dataHint?: string;
}

export interface ContentDefinitionSummary {
  slug: string;
  name: string;
  entryCount?: number;
}

export interface AppNavOptions {
  appSlug: string;
  definitions: ContentDefinitionSummary[];
  hasPublishedVersion: boolean;
  isDataVisible?: boolean;
}

type TranslationFn = (key: string) => string;

export function getAppNavSections(
  options: AppNavOptions,
  t: TranslationFn
): AppNavSection[] {
  const { appSlug, definitions, hasPublishedVersion, isDataVisible } = options;
  const dataVisible = isDataVisible ?? hasPublishedVersion;
  const base = `/apps/${appSlug}`;

  const sections: AppNavSection[] = [
    {
      label: t("appSidebar.schema"),
      items: [
        { label: t("appSidebar.definitions"), href: `${base}/definitions`, icon: Grid3X3, testId: "nav-definitions" },
        { label: t("appSidebar.dependencies"), href: `${base}/dependencies`, icon: GitBranch, testId: "nav-dependencies" },
      ],
    },
  ];

  if (!hasPublishedVersion) {
    sections.push({
      label: t("appSidebar.data"),
      items: [],
      disabledHint: t("appSidebar.publishFirst"),
    });
  } else if (!dataVisible) {
    sections.push({
      label: t("appSidebar.data"),
      items: [],
      dataHint: t("appSidebar.dataHint"),
    });
  } else {
    sections.push({
      label: t("appSidebar.data"),
      items: definitions.map((def) => ({
        label: def.name,
        href: `${base}/data/${def.slug}`,
        icon: Database,
        testId: `nav-data-${def.slug}`,
        badge: def.entryCount != null ? String(def.entryCount) : undefined,
      })),
    });
  }

  sections.push({
    label: t("appSidebar.config"),
    items: [
      { label: t("appSidebar.overview"), href: `${base}/overview`, icon: Info, testId: "nav-overview" },
      { label: t("appSidebar.versions"), href: `${base}/versions`, icon: FileStack, testId: "nav-versions" },
      { label: t("appSidebar.revisions"), href: `${base}/revisions`, icon: History, testId: "nav-revisions" },
    ],
  });

  return sections;
}
