package com.careercopilot.discovery;

import java.util.regex.Pattern;

public final class JobNormalizer {

    private static final Pattern COMPANY_SUFFIXES = Pattern.compile(
            "\\b(inc|corp|co|llc|ltd|gmbh|corporation|incorporated|limited|solutions|systems)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SENIORITY_WORDS = Pattern.compile(
            "\\b(senior|sr|lead|junior|jr|staff|principal|i|ii|iii|iv|v|level\\s*\\d+|l\\d+)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public static String generateDedupKey(String company, String title, String location) {
        if (company == null) {
            company = "unknown";
        }
        if (title == null) {
            title = "unknown";
        }
        if (location == null) {
            location = "unknown";
        }

        String c = company.toLowerCase()
                .replaceAll(COMPANY_SUFFIXES.pattern(), "")
                .replaceAll("[^a-z0-9]", "")
                .trim();

        String t = title.toLowerCase()
                .replaceAll(SENIORITY_WORDS.pattern(), "")
                .replaceAll("[\\[\\(].*?[\\]\\)]", "") // remove brackets/parenthesis contents like [Remote] or (Hybrid)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();

        String l = location.toLowerCase();
        if (l.contains("remote") || l.contains("anywhere") || l.contains("virtual") || l.contains("wfh")) {
            l = "remote";
        } else {
            l = l.replaceAll("[^a-z0-9]", "").trim();
        }

        return c + "|" + t + "|" + l;
    }
}
