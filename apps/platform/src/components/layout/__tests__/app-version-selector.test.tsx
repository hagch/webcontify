import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AppVersionSelector } from "../app-version-selector";
import type { AppVersionResponse } from "@/generated/app";

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => {
    const map: Record<string, string> = {
      draft: "Draft",
      active: "Active",
      viewingVersion: "Viewing version",
      switchToDraft: "Switch to Draft",
    };
    return map[key] ?? key;
  },
}));

const mockOnSelect = vi.fn();

const baseVersions: AppVersionResponse[] = [
  {
    id: "version-1",
    version: "1.0.0",
    publishedAt: "2026-04-01T10:00:00Z",
  },
  {
    id: "version-2",
    version: "0.9.0",
    publishedAt: "2026-03-01T10:00:00Z",
  },
];

describe("AppVersionSelector", () => {
  it("shows current selection as Draft with change count in trigger button", () => {
    render(
      <AppVersionSelector
        selectedVersion="draft"
        versions={baseVersions}
        hasDraft={true}
        hasUnpublishedChanges={true}
        unpublishedChangeCount={3}
        onSelect={mockOnSelect}
      />
    );

    const trigger = screen.getByTestId("version-selector-trigger");
    expect(trigger).toBeInTheDocument();
    expect(trigger).toHaveTextContent("Draft");
    expect(trigger).toHaveTextContent("3");
  });

  it("shows only Draft label (no trigger button) when no versions exist", () => {
    render(
      <AppVersionSelector
        selectedVersion="draft"
        versions={[]}
        hasDraft={true}
        hasUnpublishedChanges={false}
        unpublishedChangeCount={0}
        onSelect={mockOnSelect}
      />
    );

    expect(screen.queryByTestId("version-selector-trigger")).not.toBeInTheDocument();
    expect(screen.getByText("Draft")).toBeInTheDocument();
  });

  it("opens popover and shows version list on click", async () => {
    const user = userEvent.setup();

    render(
      <AppVersionSelector
        selectedVersion="draft"
        versions={baseVersions}
        hasDraft={true}
        hasUnpublishedChanges={false}
        unpublishedChangeCount={0}
        onSelect={mockOnSelect}
      />
    );

    const trigger = screen.getByTestId("version-selector-trigger");
    await user.click(trigger);

    // Draft option visible in popover
    expect(screen.getAllByText("Draft").length).toBeGreaterThan(0);
    // Active version (first) marked with (Active)
    expect(screen.getByText("1.0.0 (Active)")).toBeInTheDocument();
    // Older version without suffix
    expect(screen.getByText("0.9.0")).toBeInTheDocument();
  });

  it("calls onSelect when a version is clicked", async () => {
    const user = userEvent.setup();
    mockOnSelect.mockClear();

    render(
      <AppVersionSelector
        selectedVersion="draft"
        versions={baseVersions}
        hasDraft={true}
        hasUnpublishedChanges={false}
        unpublishedChangeCount={0}
        onSelect={mockOnSelect}
      />
    );

    const trigger = screen.getByTestId("version-selector-trigger");
    await user.click(trigger);

    const versionButton = screen.getByText("0.9.0");
    await user.click(versionButton);

    expect(mockOnSelect).toHaveBeenCalledOnce();
    expect(mockOnSelect).toHaveBeenCalledWith("version-2");
  });
});
