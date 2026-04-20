"use client";

import { useState, useCallback, useRef, useEffect, useMemo } from "react";
import { useTranslations } from "next-intl";
import { Search, Plus, Eye, Package } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import {
  useListApps,
} from "@/generated/app";
import {
  useListContentDefinitions,
  type ContentDefinitionResponse,
} from "@/generated/content";

interface ContentSpotlightProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentAppSlug: string;
  onCreateDefinition: () => void;
  onAddGhostNode: (definition: ContentDefinitionResponse, appName: string) => void;
  onFocusNode: (definitionId: string) => void;
  canCreate?: boolean;
}

interface SpotlightItem {
  id: string;
  type: "create" | "own" | "external";
  label: string;
  sublabel?: string;
  definition?: ContentDefinitionResponse;
  appName?: string;
}

export function ContentSpotlight({
  open,
  onOpenChange,
  currentAppSlug,
  onCreateDefinition,
  onAddGhostNode,
  onFocusNode,
  canCreate = false,
}: ContentSpotlightProps) {
  const t = useTranslations("content");
  const [search, setSearch] = useState("");
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const { data: appsData } = useListApps();
  const { data: ownDefsData } = useListContentDefinitions(currentAppSlug);

  const allApps = useMemo(() => appsData?.data?.items ?? [], [appsData]);
  const ownDefinitions = useMemo(
    () => ownDefsData?.data?.items ?? [],
    [ownDefsData]
  );

  const otherApps = useMemo(
    () => allApps.filter((app) => app.slug !== currentAppSlug && app.status === "ACTIVE"),
    [allApps, currentAppSlug]
  );

  const items = useMemo(() => {
    const result: SpotlightItem[] = [];
    const query = search.toLowerCase();

    // Show "Create new definition" at top only if the user can create
    if (canCreate) {
      result.push({
        id: "__create__",
        type: "create",
        label: t("spotlight.createNew"),
      });
    }

    // Own definitions matching query
    for (const def of ownDefinitions) {
      if (!query || def.name.toLowerCase().includes(query)) {
        result.push({
          id: def.id,
          type: "own",
          label: def.name,
          sublabel: def.description,
          definition: def,
        });
      }
    }

    // External definitions grouped by app
    for (const app of otherApps) {
      // We don't have external definitions loaded here, so we show the app as a group header
      // The parent component should provide external definitions if needed
      // For now, show apps that match the search
      if (!query || app.name.toLowerCase().includes(query)) {
        result.push({
          id: `app-${app.id}`,
          type: "external",
          label: app.name,
          appName: app.name,
        });
      }
    }

    return result;
  }, [search, ownDefinitions, otherApps, t]);

  // Reset state when opening — track previous open state to detect transitions
  const [prevOpen, setPrevOpen] = useState(open);
  if (open && !prevOpen) {
    setSearch("");
    setSelectedIndex(0);
  }
  if (open !== prevOpen) {
    setPrevOpen(open);
  }

  useEffect(() => {
    if (open) {
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [open]);

  // Scroll selected item into view
  useEffect(() => {
    if (!listRef.current) return;
    const selectedEl = listRef.current.querySelector(
      `[data-index="${selectedIndex}"]`
    );
    selectedEl?.scrollIntoView({ block: "nearest" });
  }, [selectedIndex]);

  const handleSelect = useCallback(
    (item: SpotlightItem) => {
      if (item.type === "create") {
        if (!canCreate) return;
        onCreateDefinition();
        onOpenChange(false);
      } else if (item.type === "own" && item.definition) {
        onFocusNode(item.definition.id);
        onOpenChange(false);
      } else if (item.type === "external" && item.definition && item.appName) {
        onAddGhostNode(item.definition, item.appName);
        onOpenChange(false);
      }
    },
    [onCreateDefinition, onFocusNode, onAddGhostNode, onOpenChange]
  );

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      switch (e.key) {
        case "ArrowDown":
          e.preventDefault();
          setSelectedIndex((prev) => Math.min(prev + 1, items.length - 1));
          break;
        case "ArrowUp":
          e.preventDefault();
          setSelectedIndex((prev) => Math.max(prev - 1, 0));
          break;
        case "Enter":
          e.preventDefault();
          if (items[selectedIndex]) {
            handleSelect(items[selectedIndex]);
          }
          break;
        case "Escape":
          e.preventDefault();
          onOpenChange(false);
          break;
      }
    },
    [items, selectedIndex, handleSelect, onOpenChange]
  );

  if (!open) return null;

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 z-50 bg-black/20 backdrop-blur-xs"
        onClick={() => onOpenChange(false)}
        data-testid="spotlight-backdrop"
      />

      {/* Spotlight Panel */}
      <div
        className="fixed left-1/2 top-[20%] z-50 w-full max-w-md -translate-x-1/2 rounded-xl border bg-background shadow-2xl ring-1 ring-foreground/10"
        data-testid="content-spotlight"
        onKeyDown={handleKeyDown}
      >
        {/* Search Input */}
        <div className="relative border-b px-3">
          <Search className="absolute left-4 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            ref={inputRef}
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setSelectedIndex(0);
            }}
            placeholder={t("spotlight.placeholder")}
            className="border-0 bg-transparent dark:bg-transparent pl-8 h-11 text-sm shadow-none focus-visible:ring-0 focus-visible:border-transparent"
            data-testid="spotlight-search-input"
          />
        </div>

        {/* Results List */}
        <div
          ref={listRef}
          className="max-h-[300px] overflow-y-auto p-1"
          data-testid="spotlight-results"
        >
          {items.length === 0 ? (
            <div className="py-6 text-center text-sm text-muted-foreground">
              {t("spotlight.noResults")}
            </div>
          ) : (
            items.map((item, index) => (
              <Button
                key={item.id}
                variant="ghost"
                data-index={index}
                className={cn(
                  "w-full justify-start gap-3 h-auto py-2 px-3 text-sm",
                  selectedIndex === index && "bg-primary/10"
                )}
                onClick={() => handleSelect(item)}
                data-testid={`spotlight-item-${item.id}`}
              >
                {item.type === "create" && (
                  <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-primary/10">
                    <Plus className="size-4 text-primary" />
                  </div>
                )}
                {item.type === "own" && (
                  <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-gradient-to-br from-primary to-primary/70">
                    <span className="text-[10px] font-bold text-primary-foreground">
                      {item.label.charAt(0).toUpperCase()}
                    </span>
                  </div>
                )}
                {item.type === "external" && (
                  <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-muted">
                    <Package className="size-3.5 text-muted-foreground" />
                  </div>
                )}
                <div className="flex flex-1 flex-col min-w-0">
                  <span className="truncate font-medium">{item.label}</span>
                  {item.sublabel && (
                    <span className="truncate text-xs text-muted-foreground">
                      {item.sublabel}
                    </span>
                  )}
                  {item.type === "external" && item.appName && (
                    <span className="text-xs text-muted-foreground">
                      {item.appName}
                    </span>
                  )}
                </div>
                {item.type === "own" && (
                  <Eye className="size-3.5 shrink-0 text-muted-foreground" />
                )}
              </Button>
            ))
          )}
        </div>

        {/* Footer hint */}
        <div className="border-t px-3 py-2">
          <div className="flex items-center gap-4 text-[10px] text-muted-foreground">
            <span>
              <kbd className="rounded border bg-muted px-1 py-0.5 text-[10px]">&#8984; K</kbd>{" "}
              open
            </span>
            <span>
              <kbd className="rounded border bg-muted px-1 py-0.5 text-[10px]">&uarr;&darr;</kbd>{" "}
              navigate
            </span>
            <span>
              <kbd className="rounded border bg-muted px-1 py-0.5 text-[10px]">Enter</kbd>{" "}
              select
            </span>
            <span>
              <kbd className="rounded border bg-muted px-1 py-0.5 text-[10px]">Esc</kbd>{" "}
              close
            </span>
          </div>
        </div>
      </div>
    </>
  );
}
