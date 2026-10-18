package com.forgeai.code;

import java.util.regex.Pattern;

/**
 * File paths in the code workspace: relative, forward slashes, no "..", no hidden Git data and
 * no real {@code .env} files (only examples), so nothing a generated change set contains can point
 * outside the project or carry secrets.
 */
final class CodePaths {

    static final int MAX_LENGTH = 300;
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._@+-]+");

    private CodePaths() {
    }

    /** The normalised path, or null if it is not allowed. "./src\\App.tsx" → "src/App.tsx". */
    static String normalise(String raw) {
        if (raw == null) return null;
        String path = raw.strip().replace('\\', '/');
        while (path.startsWith("./")) path = path.substring(2);
        if (path.isEmpty() || path.startsWith("/") || path.endsWith("/") || path.length() > MAX_LENGTH) return null;
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || !SEGMENT.matcher(segment).matches()) return null;
        }
        if (segments[0].equals(".git")) return null;
        if (segments[segments.length - 1].equals(".env")) return null;
        return path;
    }
}
