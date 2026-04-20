package io.webcontify.content.management.query;

import java.util.List;

public record ContentQuery(List<Filter> filters, List<SortCriterion> sorts, CursorPageRequest page) {}
