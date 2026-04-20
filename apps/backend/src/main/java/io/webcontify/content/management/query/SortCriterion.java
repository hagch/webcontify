package io.webcontify.content.management.query;

public record SortCriterion(String column, Direction direction) {

    public enum Direction {
        ASC, DESC
    }
}
