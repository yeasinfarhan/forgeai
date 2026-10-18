package com.forgeai.code;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodePathsTest {

    @Test
    void onlySafeRelativePathsAreAllowed() {
        assertThat(CodePaths.normalise("./src\\main/App.java")).isEqualTo("src/main/App.java");
        assertThat(CodePaths.normalise(".env.example")).isEqualTo(".env.example");
        assertThat(CodePaths.normalise("/etc/passwd")).isNull();
        assertThat(CodePaths.normalise("src/../../x")).isNull();
        assertThat(CodePaths.normalise(".git/config")).isNull();
        assertThat(CodePaths.normalise("backend/.env")).isNull();
        assertThat(CodePaths.normalise("src//App.java")).isNull();
        assertThat(CodePaths.normalise("src/my file.txt")).isNull();
    }

    @Test
    void testFilesAndBuildFilesAreRecognised() {
        assertThat(CodePaths.isTestFile("backend/src/test/java/com/acme/InvoiceService.java")).isTrue();
        assertThat(CodePaths.isTestFile("backend/src/main/java/com/acme/InvoiceServiceTest.java")).isTrue();
        assertThat(CodePaths.isTestFile("backend/src/main/java/com/acme/InvoiceApiIT.java")).isTrue();
        assertThat(CodePaths.isTestFile("frontend/src/lib/api.test.ts")).isTrue();
        assertThat(CodePaths.isTestFile("frontend/src/__tests__/App.tsx")).isTrue();
        assertThat(CodePaths.isTestFile("web/app.spec.js")).isTrue();
        assertThat(CodePaths.isTestFile("tests/conftest.py")).isTrue();
        assertThat(CodePaths.isTestFile("api/test_invoices.py")).isTrue();
        assertThat(CodePaths.isTestFile("store/store_test.go")).isTrue();
        assertThat(CodePaths.isTestFile("frontend/vitest.config.ts")).isTrue();
        assertThat(CodePaths.isTestFile("backend/src/main/java/com/acme/InvoiceService.java")).isFalse();
        assertThat(CodePaths.isTestFile("docs/AUDIT.md")).isFalse();
        assertThat(CodePaths.isTestFile("src/latest.ts")).isFalse();
        assertThat(CodePaths.isBuildFile("backend/pom.xml")).isTrue();
        assertThat(CodePaths.isBuildFile("frontend/package.json")).isTrue();
        assertThat(CodePaths.isBuildFile("frontend/src/package.ts")).isFalse();
    }

    @Test
    void wrappingCodeFencesAreRemoved() {
        assertThat(CodeAssistant.stripFence("```java\nclass A {}\n```")).isEqualTo("class A {}\n");
        assertThat(CodeAssistant.stripFence("class A {}\n")).isEqualTo("class A {}\n");
        assertThat(CodeAssistant.lines("a\nb\n")).isEqualTo(2);
        assertThat(CodeAssistant.lines("a\nb")).isEqualTo(2);
    }
}
