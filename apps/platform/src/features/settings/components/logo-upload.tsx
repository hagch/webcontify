"use client";

import { useRef, useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

interface LogoUploadProps {
  label: string;
  currentLogoUrl?: string;
  onFileSelect: (file: File | null) => void;
  onDelete?: () => void;
  accept?: string;
  maxSizeMb?: number;
}

export function LogoUpload({
  label,
  currentLogoUrl,
  onFileSelect,
  onDelete,
  accept = "image/png, image/jpeg",
  maxSizeMb = 2,
}: LogoUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Cleanup object URL on unmount
  useEffect(() => {
    return () => {
      if (previewUrl && previewUrl.startsWith("blob:")) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    const maxBytes = maxSizeMb * 1024 * 1024;
    if (file.size > maxBytes) {
      setError(`Maximale Dateigröße: ${maxSizeMb} MB`);
      return;
    }

    setError(null);

    // Revoke previous blob URL
    if (previewUrl && previewUrl.startsWith("blob:")) {
      URL.revokeObjectURL(previewUrl);
    }

    const objectUrl = URL.createObjectURL(file);
    setPreviewUrl(objectUrl);
    onFileSelect(file);
  }

  function handleDelete() {
    if (previewUrl && previewUrl.startsWith("blob:")) {
      URL.revokeObjectURL(previewUrl);
    }
    setPreviewUrl(null);
    setError(null);
    if (inputRef.current) {
      inputRef.current.value = "";
    }
    onFileSelect(null);
    onDelete?.();
  }

  const displayUrl = previewUrl ?? currentLogoUrl;
  const hasImage = Boolean(displayUrl);

  return (
    <div className="flex flex-col gap-2">
      <span className="text-sm font-medium">{label}</span>
      <Card className="w-full max-w-xs">
        <CardContent className="flex flex-col items-center gap-3 p-4">
          <div
            className={cn(
              "flex h-24 w-full items-center justify-center rounded-md border border-dashed border-border bg-muted/30",
              hasImage && "border-solid border-border bg-transparent"
            )}
          >
            {displayUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={displayUrl}
                alt={label}
                className="max-h-20 max-w-full object-contain"
              />
            ) : (
              <span className="text-xs text-muted-foreground">Kein Bild</span>
            )}
          </div>

          {error && (
            <p className="text-xs text-destructive">{error}</p>
          )}

          <div className="flex w-full gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="flex-1"
              onClick={() => inputRef.current?.click()}
            >
              Hochladen
            </Button>
            {hasImage && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleDelete}
              >
                Entfernen
              </Button>
            )}
          </div>

          <input
            ref={inputRef}
            type="file"
            accept={accept}
            className="hidden"
            onChange={handleFileChange}
            aria-label={`${label} hochladen`}
          />
        </CardContent>
      </Card>
    </div>
  );
}
