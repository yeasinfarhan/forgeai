package com.forgeai.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Prompt templates stored as versioned files in {@code src/main/resources/prompts/<name>.md}.
 *
 * <p>Placeholders are written {@code {{name}}}. Substitution is a single pass over the template,
 * so text supplied by users can never introduce new placeholders. A missing value is an error,
 * not a silent blank.
 */
@Component
public class PromptTemplates {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9]*)\\s*}}");

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String render(String name, Map<String, ?> values) {
        String template = cache.computeIfAbsent(name, PromptTemplates::load);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!values.containsKey(key)) {
                throw new IllegalArgumentException("Prompt '" + name + "' needs a value for {{" + key + "}}");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(values.get(key))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String load(String name) {
        ClassPathResource resource = new ClassPathResource("prompts/" + name + ".md");
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Prompt template not found: prompts/" + name + ".md", ex);
        }
    }
}
