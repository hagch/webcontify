import { AppHeader } from "@/components/layout/app-header";

export default function EmailSettingsPage() {
  return (
    <>
      <AppHeader
        breadcrumbs={[{ label: "Settings" }, { label: "Email" }]}
      />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">
          Email Settings
        </h1>
        <p className="text-sm text-muted-foreground">
          Configure SMTP settings for transactional emails.
        </p>
      </div>
    </>
  );
}
