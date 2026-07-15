---
name: web-design-guidelines
description: |
  Review UI code and designs against Web Interface Guidelines for accessibility, usability,
  layout correctness, and best practices. Fetches latest rules from source before reviewing.

  Use this skill when:
  - User asks to "review my UI", "audit my design", "check accessibility", "audit UX"
  - User mentions WCAG, a11y, accessibility compliance, screen reader support
  - User wants a design review, UI review, or UX review of frontend code
  - Checking for semantic HTML, proper heading hierarchy, ARIA labels, focus management
  - User says "does my UI follow best practices", "is this accessible", "check my interface"
  - Reviewing a component, page, or entire app for design guideline compliance
  - User mentions contrast ratios, color accessibility, keyboard navigation, tab order

  Do NOT use for:
  - Pure logic/business code with no UI
  - Backend code review (use frontend-code-review for mixed reviews)

  Keywords: UI review, UX review, design review, accessibility, a11y, WCAG, aria,
  screen reader, keyboard navigation, contrast, semantic HTML, focus, best practices,
  design audit, interface review, web guidelines, usability, responsive, mobile
argument-hint: <file-or-pattern>
metadata:
  author: vercel
  version: "1.0.0"
---

# Web Interface Guidelines

Review files for compliance with Web Interface Guidelines.

## How It Works

1. Fetch the latest guidelines from the source URL below
2. Read the specified files (or prompt user for files/pattern)
3. Check against all rules in the fetched guidelines
4. Output findings in the terse `file:line` format

## Guidelines Source

Fetch fresh guidelines before each review:

```
https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md
```

Use WebFetch to retrieve the latest rules. The fetched content contains all the rules and output format instructions.

## Usage

When a user provides a file or pattern argument:
1. Fetch guidelines from the source URL above
2. Read the specified files
3. Apply all rules from the fetched guidelines
4. Output findings using the format specified in the guidelines

If no files specified, ask the user which files to review.
