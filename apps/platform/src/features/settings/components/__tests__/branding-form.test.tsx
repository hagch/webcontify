import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { BrandingForm } from "../branding-form";

// Mock @/generated/tenant
vi.mock("@/generated/tenant", () => ({
  useGetTenantSelfConfig: vi.fn(),
  useUpdateTenantSelfBranding: vi.fn(),
  useUploadFile: vi.fn(),
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

  it("zeigt Skeleton während Laden", () => {
    mockUseGetTenantSelfConfig.mockReturnValue({
      data: undefined,
      isLoading: true,
    } as unknown as ReturnType<typeof useGetTenantSelfConfig>);

    render(<BrandingForm />);

    // Skeletons werden gerendert, Formular nicht
    expect(screen.queryByText("Speichern")).not.toBeInTheDocument();
    const skeletons = document.querySelectorAll(".animate-pulse");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("rendert alle Sektionen nach dem Laden", () => {
    mockUseGetTenantSelfConfig.mockReturnValue({
      data: { data: { branding: {} }, status: 200, headers: new Headers() },
      isLoading: false,
    } as unknown as ReturnType<typeof useGetTenantSelfConfig>);

    render(<BrandingForm />);

    // "Logo" und "Favicon" kommen mehrfach vor (CardTitle + LogoUpload label)
    expect(screen.getAllByText("Logo").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Favicon").length).toBeGreaterThan(0);
    expect(screen.getByText("Farben")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Speichern" })).toBeInTheDocument();
  });

  it("zeigt aktuelle Werte aus API", () => {
    mockUseGetTenantSelfConfig.mockReturnValue({
      data: {
        data: {
          branding: {
            primaryColor: "#6366f1",
            secondaryColor: "#10b981",
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

    // Hex-Inputs zeigen die API-Werte
    const inputs = screen.getAllByPlaceholderText("#000000") as HTMLInputElement[];
    const primaryInput = inputs.find((i) => i.value === "#6366f1");
    const secondaryInput = inputs.find((i) => i.value === "#10b981");
    expect(primaryInput).toBeDefined();
    expect(secondaryInput).toBeDefined();
  });

  it("zeigt Lade-Zustand beim Speichern", () => {
    mockUseGetTenantSelfConfig.mockReturnValue({
      data: { data: { branding: {} }, status: 200, headers: new Headers() },
      isLoading: false,
    } as unknown as ReturnType<typeof useGetTenantSelfConfig>);

    mockUseUpdateTenantSelfBranding.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: true,
    } as unknown as ReturnType<typeof useUpdateTenantSelfBranding>);

    render(<BrandingForm />);

    const saveButton = screen.getByRole("button", { name: "Wird gespeichert…" });
    expect(saveButton).toBeInTheDocument();
    expect(saveButton).toBeDisabled();
  });
});
