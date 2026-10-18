package com.forgeai.code;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * File paths in the code workspace: relative, forward slashes, no "..", no hidden Git data and
 * no real {@code .env} files (only examples), so nothing a generated change set contains can point
 * outside the project or carry secrets.
 */
public final class CodePaths {

    static final int MAX_LENGTH = 300;
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._@+-]+");

    /** Folders that hold tests in common project layouts. */
    private static final Set<String> TEST_FOLDERS = Set.of("test", "tests", "__tests__", "spec", "specs", "e2e");

    /** Build files test generation may update to add test dependencies. */
    private static final Set<String> BUILD_FILES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "package.json", "requirements.txt", "requirements-dev.txt",
            "pyproject.toml", "go.mod", "cargo.toml", "gemfile", "composer.json");

    /** Test runner configuration files, by name prefix. */
    private static final Set<String> TEST_CONFIG_PREFIXES = Set.of(
            "jest.config.", "vitest.config.", "playwright.config.", "karma.conf.", "pytest.ini", "conftest.py", "setuptests.");

    private CodePaths() {
    }

    /** The normalised path, or null if it is not allowed. "./src\\App.tsx" → "src/App.tsx". */
    public static String normalise(String raw) {
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

    /**
     * True for a test file or test runner configuration: a file in a test folder
     * ({@code src/test/...}, {@code tests/}, {@code __tests__/} …) or named like a test
     * ({@code *.test.ts}, {@code *.spec.js}, {@code InvoiceServiceTest.java}, {@code test_api.py},
     * {@code store_test.go} …). Expects a normalised path.
     */
    public static boolean isTestFile(String path) {
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if (TEST_FOLDERS.contains(segments[i].toLowerCase(Locale.ROOT))) return true;
        }
        String name = segments[segments.length - 1];
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains(".test.") || lower.contains(".spec.")) return true;
        for (String prefix : TEST_CONFIG_PREFIXES) {
            if (lower.startsWith(prefix)) return true;
        }
        int dot = name.indexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base.endsWith("Test") || base.endsWith("Tests") || base.matches(".*[a-z0-9]IT")
                || base.toLowerCase(Locale.ROOT).startsWith("test_") || base.toLowerCase(Locale.ROOT).endsWith("_test");
    }

    /** True for a build file such as {@code pom.xml} or {@code package.json}. Expects a normalised path. */
    public static boolean isBuildFile(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return BUILD_FILES.contains(name);
    }
}
