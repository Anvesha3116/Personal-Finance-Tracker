package com.financetracker.web;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal multipart/form-data reader: text fields plus uploaded files. */
final class Multipart {
    private static final Pattern NAME = Pattern.compile("(?:^|[;\\s])name=\"([^\"]*)\"");
    private static final Pattern FILENAME = Pattern.compile("filename=\"([^\"]*)\"");

    final Map<String, String> fields = new HashMap<>();
    final Map<String, byte[]> files = new HashMap<>();

    static Multipart parse(String contentType, byte[] body) {
        Multipart result = new Multipart();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            return result;
        }
        String boundary = null;
        for (String piece : contentType.split(";")) {
            String p = piece.trim();
            if (p.toLowerCase(Locale.ROOT).startsWith("boundary=")) {
                boundary = p.substring("boundary=".length()).replace("\"", "");
            }
        }
        if (boundary == null || boundary.isEmpty()) return result;

        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        int pos = indexOf(body, delimiter, 0);
        while (pos >= 0) {
            int start = pos + delimiter.length;
            if (start + 1 < body.length && body[start] == '-' && body[start + 1] == '-') break;   // closing boundary
            if (start + 1 < body.length && body[start] == '\r' && body[start + 1] == '\n') start += 2;
            int next = indexOf(body, delimiter, start);
            if (next < 0) break;
            int end = Math.max(start, next - 2);   // drop the CRLF before the next boundary
            result.readPart(body, start, end);
            pos = next;
        }
        return result;
    }

    private void readPart(byte[] body, int start, int end) {
        int headersEnd = indexOf(body, new byte[] {'\r', '\n', '\r', '\n'}, start);
        if (headersEnd < 0 || headersEnd > end) return;
        String headers = new String(body, start, headersEnd - start, StandardCharsets.UTF_8);
        Matcher name = NAME.matcher(headers);
        if (!name.find()) return;
        Matcher filename = FILENAME.matcher(headers);
        byte[] data = Arrays.copyOfRange(body, headersEnd + 4, Math.max(headersEnd + 4, end));
        if (filename.find()) {
            if (!filename.group(1).isEmpty()) files.put(name.group(1), data);   // empty filename = no file chosen
        } else {
            fields.put(name.group(1), new String(data, StandardCharsets.UTF_8));
        }
    }

    private static int indexOf(byte[] hay, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
