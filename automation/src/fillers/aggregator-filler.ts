// Remotive / Himalayas / Arbeitnow Redirect Filler
// These are job aggregator listing pages — we follow the Apply link
// to reach the actual ATS, then re-detect and delegate to the right filler.

import { Page } from 'playwright';
import { ResumeProfile, FillerResult } from './types';
import { detectPlatform } from '../platform-detector';
import { fillWithStrategy } from './index';

const TIMEOUT = 20_000;

/** Click the apply button on an aggregator listing page and follow the redirect. */
async function clickApplyAndNavigate(page: Page, source: string, logs: string[]): Promise<boolean> {
  const applySelectors = [
    'a:has-text("Apply for this job")',
    'a:has-text("Apply Now")',
    'a:has-text("Apply")',
    'button:has-text("Apply for this job")',
    'button:has-text("Apply")',
    '[data-testid*="apply"]',
    '.apply-btn',
    '.btn-apply',
    'a[href*="apply"]',
  ];

  for (const sel of applySelectors) {
    try {
      const btn = page.locator(sel).first();
      if (await btn.count() === 0) continue;
      const href = await btn.getAttribute('href').catch(() => null);
      if (href && href.startsWith('http')) {
        logs.push(`[${source}] Navigating to apply URL: ${href}`);
        await page.goto(href, { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
        return true;
      }
      logs.push(`[${source}] Clicking apply button`);
      await btn.click();
      await page.waitForTimeout(2000);
      return true;
    } catch { /* try next */ }
  }

  logs.push(`[${source}] WARN: No apply button found — page may already be on apply form`);
  return false;
}

export async function aggregatorFill(
  page: Page,
  resume: ResumeProfile,
  pdfResumePath: string,
  coverLetterPath: string,
  logs: string[],
  isLive: boolean,
  sourceLabel: 'remotive' | 'himalayas' | 'arbeitnow',
): Promise<FillerResult> {
  logs.push(`[${sourceLabel}] Starting aggregator redirect filler`);

  // Navigate to the apply form
  await clickApplyAndNavigate(page, sourceLabel, logs);
  await page.waitForTimeout(1500);

  // Re-detect the real ATS platform now that we've followed the redirect
  const currentUrl = page.url();
  const detection = await detectPlatform(currentUrl, page);
  logs.push(`[${sourceLabel}] Redirected to: ${currentUrl} → detected: ${detection.platform} (${detection.confidence})`);

  // Delegate to the appropriate strategy
  return fillWithStrategy(detection.platform, page, resume, pdfResumePath, coverLetterPath, logs, isLive);
}
