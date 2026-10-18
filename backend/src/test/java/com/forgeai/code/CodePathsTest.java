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
    void wrappingCodeFencesAreRemoved() {
        assertThat(CodeAssistant.stripFence("```java\nclass A {}\n```")).isEqualTo("class A {}\n");
        assertThat(CodeAssistant.stripFence("class A {}\n")).isEqualTo("class A {}\n");
        assertThat(CodeAssistant.lines("a\nb\n")).isEqualTo(2);
        assertThat(CodeAssistant.lines("a\nb")).isEqualTo(2);
    }
}
