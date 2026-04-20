package io.webcontify.content.management.query;

public record CursorPageRequest(String cursor, int size) {

    public int effectiveSize() {
        if (size >= 1 && size <= 100) {
            return size;
        }
        return 20;
    }
}
