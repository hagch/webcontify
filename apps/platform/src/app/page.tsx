import { useTranslations } from "next-intl";

export default function Home() {
  const t = useTranslations("home");

  return (
    <div className="flex min-h-screen items-center justify-center">
      <main className="flex flex-col items-center gap-4">
        <h1 className="text-4xl font-bold" style={{ color: "hsl(var(--primary))" }}>
          {t("title")}
        </h1>
        <p className="text-lg" style={{ color: "hsl(var(--muted-foreground))" }}>
          {t("description")}
        </p>
      </main>
    </div>
  );
}
