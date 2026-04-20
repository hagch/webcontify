package io.webcontify.content.management.query;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext) {}
