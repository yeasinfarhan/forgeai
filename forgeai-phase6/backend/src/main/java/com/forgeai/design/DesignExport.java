package com.forgeai.design;

import java.text.Normalizer;
import java.util.Locale;

/**
 * A file produced from a design document (SQL script, OpenAPI document).
 *
 * @param fileName    suggested name for the download
 * @param contentType media type of {@code body}
 */
public record DesignExport(String fileName, String contentType, String body) {

    /** "Invoice Tracker!" → "invoice-tracker"; falls back to "project". */
    public static String slug(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 60) slug = slug.substring(0, 60).replaceAll("-+$", "");
        return slug.isEmpty() ? "project" : slug;
    }
}
