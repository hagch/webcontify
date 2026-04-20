import { AppHeader } from "@/components/layout/app-header";
import { BrandingForm } from "@/features/settings/components/branding-form";

export default function BrandingSettingsPage() {
  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Einstellungen" }, { label: "Branding" }]} />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Branding</h1>
          <p className="text-sm text-muted-foreground">
            Passe das Erscheinungsbild deines Workspaces an.
          </p>
        </div>
        <BrandingForm />
      </div>
    </>
  );
}
