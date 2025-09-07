package org.example.bench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Queries {
    private final List<String> qs;
    private int i = 0;

    private Queries(List<String> qs) { this.qs = qs.isEmpty() ? List.of("lucene") : qs; }

    public static Queries fromFileOrDefault(Path p) {
        try {
            if (p != null && Files.isRegularFile(p)) {
                List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                lines.removeIf(s -> s == null || s.isBlank());
                return new Queries(lines);
            }
        } catch (IOException ignored) {}
        return new Queries(List.of("lucene", "solr", "\"text search\"", "indexing AND retrieval"));
    }

    public String next() {
        String v = qs.get(i);
        i = (i + 1) % qs.size();
        return v;
    }
}
