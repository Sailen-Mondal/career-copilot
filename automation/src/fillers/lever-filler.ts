// Lever Form Filler
// Handles jobs.lever.co/… job pages.
// Lever shows a description page first — we must click Apply to reach the form.

import { Page } from 'playwright';
import { ResumeProfile, FillerResult } from './types';
import { genericFill } from './generic-filler';

const TIMEOUT = 20_000;

export async function leverFill(
  page: Page,
  resume: ResumeProfile,
  pdfResumePath: string,
  coverLetterPath: string,
  logs: string[],
  isLive: boolean,
): Promise<FillerResult> {
  logs.push('[lever] Starting Lever filler');

  // ── Step 1: Navigate to the apply form ───────────────────────────────────
  // Lever shows a job description; we click the Apply button
  const applyBtn = page.locator(
    'a:has-text("Apply for this job"), a:has-text("Apply Now"), a[href*="/apply"], button:has-text("Apply")'
  ).first();

  if (await applyBtn.count() > 0) {
    const href = await applyBtn.getAttribute('href');
    if (href && href.startsWith('http')) {
      logs.push(`[lever] Navigating to apply URL: ${href}`);
      await page.goto(href, { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
    } else {
      logs.push('[lever] Clicking Apply button');
      await applyBtn.click();
      await page.waitForTimeout(2000);
    }
  } else {
    // Check if we're already on the apply page
    const alreadyApply = (await page.locator('form, input[type="text"]').count()) > 0;
    if (!alreadyApply) {
      logs.push('[lever] WARN: No Apply button found, attempting to fill current page');
    }
  }

  await page.waitForTimeout(1500);

  // ── Step 2: Delegate actual form-filling to genericFill (中央 Brain) ──────
  logs.push('[lever] Delegating actual form filling to genericFill brain');
  return genericFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive);
}
