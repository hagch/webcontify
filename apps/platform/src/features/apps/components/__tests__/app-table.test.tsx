import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { AppTable } from "../app-table";
import type { AppResponse } from "@/generated/app";

// Mock next-intl
vi.mock("next-intl", () => ({
  useTranslations: (ns: string) => {
    const translator = (key: string, params?: Record<string, unknown>) => {
      let result = `${ns}.${key}`;
      if (params) {
        for (const [k, v] of Object.entries(params)) {
          result = result.replace(`{${k}}`, String(v));
        }
      }
      return result;
    };
    translator.has = () => false;
    return translator;
  },
}));

// Mock next/link
vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const mockApp: AppResponse = {
  id: "test-id",
  name: "Test App",
  slug: "test-app",
  description: "A test application",
  status: "ACTIVE",
  hasDraft: false,
  createdAt: "2026-01-01T00:00:00Z",
  createdBy: "user-1",
  updatedAt: "2026-01-02T00:00:00Z",
  updatedBy: "user-1",
};

describe("AppTable", () => {
  it("renders loading skeleton when isLoading", () => {
    render(<AppTable apps={[]} isLoading={true} isRefetching={false} />);
    // AppTableSkeleton renders a Table with Skeleton elements
    const skeletons = document.querySelectorAll("[data-slot='skeleton']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("renders empty state when no apps", () => {
    render(<AppTable apps={[]} isLoading={false} isRefetching={false} />);
    expect(screen.getByTestId("empty-state")).toBeInTheDocument();
    expect(screen.getByText("apps.emptyState.title")).toBeInTheDocument();
  });

  it("renders app rows", () => {
    render(<AppTable apps={[mockApp]} isLoading={false} isRefetching={false} />);
    expect(screen.getByText("Test App")).toBeInTheDocument();
    expect(screen.getByTestId("app-table")).toBeInTheDocument();
    expect(screen.getByTestId("app-table-row")).toBeInTheDocument();
  });

  it("shows draft badge when hasDraft is true", () => {
    const draftApp: AppResponse = { ...mockApp, hasDraft: true };
    render(<AppTable apps={[draftApp]} isLoading={false} isRefetching={false} />);
    const hasDraftElements = screen.getAllByText("apps.hasDraft");
    // One in the table header, one as the badge in the row
    expect(hasDraftElements.length).toBe(2);
    const badge = hasDraftElements.find((el) => el.getAttribute("data-slot") === "badge");
    expect(badge).toBeDefined();
  });

  it("links app name to detail page", () => {
    render(<AppTable apps={[mockApp]} isLoading={false} isRefetching={false} />);
    const link = screen.getByText("Test App").closest("a");
    expect(link).toHaveAttribute("href", "/apps/test-id");
  });
});
