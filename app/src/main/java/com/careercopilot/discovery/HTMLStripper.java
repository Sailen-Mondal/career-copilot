package com.careercopilot.discovery;

public final class HTMLStripper {

    public static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        // Remove tags and normalize whitespace
        return html.replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
