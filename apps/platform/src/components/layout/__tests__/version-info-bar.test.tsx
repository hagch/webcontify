import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VersionInfoBar } from "../version-info-bar";

// Mock next-intl
vi.mock("next-intl", () => ({
  useTranslations: () => (key: string, params?: Record<string, unknown>) => {
    if (params) {
      return `${key}:${JSON.stringify(params)}`;
    }
    return key;
  },
}));

// Mock the context
const mockSetSelectedVersion = vi.fn();

vi.mock("@/contexts/app-version-context", () => ({
  useAppVersion: vi.fn(),
}));

import { useAppVersion } from "@/contexts/app-version-context";

const mockUseAppVersion = vi.mocked(useAppVersion);

describe("VersionInfoBar", () => {
  it("renders nothing when isReadOnly is false (draft mode)", () => {
    mockUseAppVersion.mockReturnValue({
      isReadOnly: false,
      isDraft: true,
      isActive: false,
      isDataVisible: true,
      selectedVersion: "draft",
      setSelectedVersion: mockSetSelectedVersion,
      versions: [],
      app: {} as never,
    });

    const { container } = render(<VersionInfoBar />);
    expect(container.firstChild).toBeNull();
  });

  it("renders the version label when isReadOnly is true", () => {
    mockUseAppVersion.mockReturnValue({
      isReadOnly: true,
      isDraft: false,
      isActive: true,
      isDataVisible: true,
      selectedVersion: "version-123",
      setSelectedVersion: mockSetSelectedVersion,
      versions: [{ id: "version-123", version: "1.0.0" } as never],
      app: {} as never,
    });

    render(<VersionInfoBar />);

    const statusEl = screen.getByRole("status");
    expect(statusEl).toBeInTheDocument();
    expect(screen.getByText(/viewingVersion/)).toBeInTheDocument();
    expect(screen.getByText(/1\.0\.0/)).toBeInTheDocument();
  });

  it("renders the switch-to-draft button when isReadOnly is true", () => {
    mockUseAppVersion.mockReturnValue({
      isReadOnly: true,
      isDraft: false,
      isActive: false,
      isDataVisible: false,
      selectedVersion: "version-456",
      setSelectedVersion: mockSetSelectedVersion,
      versions: [{ id: "version-456", version: "2.0.0" } as never],
      app: {} as never,
    });

    render(<VersionInfoBar />);

    expect(screen.getByTestId("switch-to-draft")).toBeInTheDocument();
  });

  it("falls back to selectedVersion id when version object is not found", () => {
    mockUseAppVersion.mockReturnValue({
      isReadOnly: true,
      isDraft: false,
      isActive: false,
      isDataVisible: false,
      selectedVersion: "unknown-id",
      setSelectedVersion: mockSetSelectedVersion,
      versions: [],
      app: {} as never,
    });

    render(<VersionInfoBar />);

    expect(screen.getByText(/unknown-id/)).toBeInTheDocument();
  });

  it("calls setSelectedVersion with 'draft' when switch button is clicked", async () => {
    const user = userEvent.setup();
    mockSetSelectedVersion.mockClear();

    mockUseAppVersion.mockReturnValue({
      isReadOnly: true,
      isDraft: false,
      isActive: true,
      isDataVisible: true,
      selectedVersion: "version-123",
      setSelectedVersion: mockSetSelectedVersion,
      versions: [{ id: "version-123", version: "1.0.0" } as never],
      app: {} as never,
    });

    render(<VersionInfoBar />);

    await user.click(screen.getByTestId("switch-to-draft"));

    expect(mockSetSelectedVersion).toHaveBeenCalledOnce();
    expect(mockSetSelectedVersion).toHaveBeenCalledWith("draft");
  });
});
