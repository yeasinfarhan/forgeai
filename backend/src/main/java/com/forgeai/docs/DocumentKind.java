package com.forgeai.docs;

/** The documents the AI writes for a project; both can be edited afterwards. */
public enum DocumentKind {
    README("README", "README.md", "docs-readme"),
    DEVELOPER_GUIDE("Developer guide", "docs/developer-guide.md", "docs-developer-guide");

    private final String title;
    private final String path;
    private final String prompt;

    DocumentKind(String title, String path, String prompt) {
        this.title = title;
        this.path = path;
        this.prompt = prompt;
    }

    public String title() {
        return title;
    }

    /** Where the document goes in the project package. */
    public String path() {
        return path;
    }

    /** The prompt template that writes it. */
    String prompt() {
        return prompt;
    }
}
