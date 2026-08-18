package com.backend.Ai_Gateway.provider;

import java.util.ArrayList;
import java.util.List;

/**
 * Assemble les lignes complètes à travers des fragments TCP/DataBuffer
 * (SSE OpenAI / NDJSON). Sans ça, un JSON coupé mid-line provoque un 500.
 */
final class StreamLineBuffer {

    private final StringBuilder pending = new StringBuilder();

    List<String> appendAndDrain(String chunk) {
        if (chunk != null && !chunk.isEmpty()) {
            pending.append(chunk);
        }
        List<String> lines = new ArrayList<>();
        while (true) {
            int lf = indexOf(pending, '\n');
            if (lf < 0) {
                break;
            }
            int end = lf;
            if (end > 0 && pending.charAt(end - 1) == '\r') {
                end--;
            }
            lines.add(pending.substring(0, end));
            pending.delete(0, lf + 1);
        }
        return lines;
    }

    /** Reste sans newline finale (généralement inutile pour SSE bien formé). */
    List<String> flush() {
        if (pending.isEmpty()) {
            return List.of();
        }
        String rest = pending.toString();
        pending.setLength(0);
        return List.of(rest);
    }

    private static int indexOf(StringBuilder sb, char c) {
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }
}
