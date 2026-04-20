"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import {
  useSearchContentEntries,
  type EntryReferenceResponse,
  type EntryReferenceListResponse,
} from "@/generated/content";

const DEBOUNCE_MS = 300;

/**
 * Custom hook wrapping the Orval-generated search endpoint with:
 * - 300ms debounce on search term
 * - Accumulated results (cursor "load more" appends)
 * - Returns { results, isLoading, search, loadMore, hasMore }
 */
export function useEntrySearch(appSlug: string, defSlug: string) {
  const [searchTerm, setSearchTerm] = useState("");
  const [debouncedTerm, setDebouncedTerm] = useState("");
  const [cursor, setCursor] = useState<string | undefined>(undefined);
  const [accumulated, setAccumulated] = useState<EntryReferenceResponse[]>([]);
  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Debounce the search term
  useEffect(() => {
    if (debounceTimer.current) {
      clearTimeout(debounceTimer.current);
    }
    debounceTimer.current = setTimeout(() => {
      setDebouncedTerm(searchTerm);
      setCursor(undefined);
      setAccumulated([]);
    }, DEBOUNCE_MS);

    return () => {
      if (debounceTimer.current) {
        clearTimeout(debounceTimer.current);
      }
    };
  }, [searchTerm]);

  const { data, isLoading } = useSearchContentEntries(appSlug, defSlug, {
    q: debouncedTerm || undefined,
    size: 20,
    cursor,
  });

  const responseData = data?.data as EntryReferenceListResponse | undefined;
  const items = responseData?.items ?? [];
  const nextCursor = responseData?.nextCursor;
  const hasNext = responseData?.hasNext ?? false;

  // Accumulate results when new data arrives
  useEffect(() => {
    if (items.length > 0) {
      if (cursor) {
        // Append for "load more"
        setAccumulated((prev) => {
          const existingIds = new Set(prev.map((r) => r.id));
          const newItems = items.filter((item) => !existingIds.has(item.id));
          return [...prev, ...newItems];
        });
      } else {
        // Fresh search
        setAccumulated(items);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  const search = useCallback((term: string) => {
    setSearchTerm(term);
  }, []);

  const loadMore = useCallback(() => {
    if (hasNext && nextCursor) {
      setCursor(nextCursor);
    }
  }, [hasNext, nextCursor]);

  return {
    results: accumulated.length > 0 ? accumulated : items,
    isLoading,
    search,
    loadMore,
    hasMore: hasNext,
  };
}
