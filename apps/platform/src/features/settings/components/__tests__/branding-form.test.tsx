import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { BrandingForm } from "../branding-form";

// Mock next-intl
vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
}));

// Mock @/generated/tenant
vi.mock("@/generated/tenant", () => ({
  useGetTenantSelfConfig: vi.fn(),
  useUpdateTenantSelfBranding: vi.fn(),
  useUploadFile: vi.fn(),
  useGetFile: vi.fn().mockReturnValue({ data: undefined }),
  getGetTenantSelfConfigQueryKey: vi.fn().mockReturnValue(["/api/v1/tenant/config"]),
}));

// Mock @tanstack/react-query
vi.mock("@tanstack/react-query", () => ({
  useQueryClient: vi.fn().mockReturnValue({
    invalidateQueries: vi.fn(),
  }),
}));

// Mock sonner
vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));


import {
  useGetTenantSelfConfig,
  useUpdateTenantSelfBranding,
  useUploadFile,
} from "@/generated/tenant";

const mockUseGetTenantSelfConfig = vi.mocked(useGetTenantSelfConfig);
const mockUseUpdateTenantSelfBranding = vi.mocked(useUpdateTenantSelfBranding);
const mockUseUploadFile = vi.mocked(useUploadFile);

function defaultMutationResult() {
  return {
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useUpdateTenantSelfBranding>;
}

function defaultUploadResult() {
  return {
    mutateAsync: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useUploadFile>;
}

describe("BrandingForm", () => {
  beforeEach(() => {
    mockUseUpdateTenantSelfBranding.mockReturnValue(defaultMutationResult());
    mockUseUploadFile.mockReturnValue(defaultUploadResult());
  });

  it("shows skeleton while loading", () => {
    mockUseGetTenantSelfConfig.mockReturnValue({
      data: undefined,
      isLoading: true,
    } as unknown as ReturnType<typeof useGetTenantSelfConfig>);

    render(<BrandingForm />);

    expect(screen.queryByText("save")).not.toBeInTheDocument();
    const skeletons = document.querySelectorAll(".animate-pulse");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("renders all sections after loading", () => {
    mockUseGetTenantSelfConfig.mockReturnValue({
      data: { data: { branding: {} }, status: 200, headers: new Headers() },
      isLoading: false,
    } as unknown as ReturnType<typeof useGetTenantSelfConfig>);

    render(<BrandingForm />);

    // Tab triggers are always visible
    expect(screen.getByRole("tab", { name: "tabLogoIdentity" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "tabColors" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "tabTypography" })).toBeInTheDocument();
    // Logo tab is active by default — Logo and Favicon uploads visible (i18n keys lowercase)
    expect(screen.getAllByText("logo").length).toBeGreaterThan(0);
    expect(screen.getAllByText("favicon").length).toBeGreaterThan(0);
    // Save button uses i18n key "save"
    expect(screen.getByRole("button", { name: "save" })).toBeInTheDocument();
  });

  it("shows current values from API", () => {
    mockUseGetTenantSelfConfig.mockReturnValue({
      data: {
        data: {
          branding: {
            primaryColor: "#6366f1",
            secondaryColor: "#10b981",
            accentColor: "#059669",
            logoStorageKey: "logos/logo.png",
            faviconStorageKey: "logos/favicon.ico",
          },
        },
        status: 200,
        headers: new Headers(),
      },
      isLoading: false,
    } as unknown as ReturnType<typeof useGetTenantSelfConfig>);

    render(<BrandingForm />);

    // Switch to Colors tab to see color inputs
    fireEvent.click(screen.getByRole("tab", { name: "tabColors" }));

    const inputs = screen.getAllByPlaceholderText("#000000") as HTMLInputElement[];
    const primaryInput = inputs.find((i) => i.value === "#6366f1");
    const secondaryInput = inputs.find((i) => i.value === "#10b981");
    const accentInput = inputs.find((i) => i.value === "#059669");
    expect(primaryInput).toBeDefined();
    expect(secondaryInput).toBeDefined();
    expect(accentInput).toBeDefined();
  });

  it("shows loading state when saving", () => {
    mockUseGetTenantSelfConfig.mockReturnValue({
      data: { data: { branding: {} }, status: 200, headers: new Headers() },
      isLoading: false,
    } as unknown as ReturnType<typeof useGetTenantSelfConfig>);

    mockUseUpdateTenantSelfBranding.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: true,
    } as unknown as ReturnType<typeof useUpdateTenantSelfBranding>);

    render(<BrandingForm />);

    const saveButton = screen.getByRole("button", { name: "..." });
    expect(saveButton).toBeInTheDocument();
    expect(saveButton).toBeDisabled();
  });
});
