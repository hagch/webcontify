"use client";

import { useEffect, useCallback } from "react";
import { X } from "lucide-react";
import { Button } from "@/components/ui/button";

interface FloatingPanelProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: React.ReactNode;
  anchorPosition?: { x: number; y: number };
  className?: string;
  "data-testid"?: string;
}

export function FloatingPanel({
  open,
  onOpenChange,
  children,
  anchorPosition,
  className,
  "data-testid": testId,
}: FloatingPanelProps) {
  const handleClose = useCallback(() => onOpenChange(false), [onOpenChange]);

  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.stopPropagation();
        handleClose();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [open, handleClose]);

  if (!open) return null;

  const positionStyle = anchorPosition
    ? { left: anchorPosition.x, top: anchorPosition.y, transform: "translateY(-50%)" }
    : { left: "50%", top: "20%", transform: "translateX(-50%)" };

  return (
    <>
      <div
        className="fixed inset-0 z-50 bg-black/20 backdrop-blur-xs"
        onClick={handleClose}
      />
      <div
        className={`fixed z-50 w-[340px] rounded-xl border bg-background p-4 shadow-2xl ring-1 ring-foreground/10 ${className ?? ""}`}
        style={positionStyle}
        data-testid={testId}
      >
        <Button
          variant="ghost"
          size="icon-xs"
          onClick={handleClose}
          className="absolute right-2 top-2 text-muted-foreground"
          data-testid="floating-panel-close"
        >
          <X className="size-4" />
        </Button>
        {children}
      </div>
    </>
  );
}
