// Platform Detector — inspects URL and page DOM to classify the ATS before filling.

import { Page } from 'playwright';

export type PlatformType =
  | 'GREENHOUSE_V2'       // boards.greenhouse.io/…
  | 'GREENHOUSE_EMBEDDED' // ?gh_jid= in URL
  | 'LEVER'               // jobs.lever.co/…
  | 'ASHBY'               // jobs.ashbyhq.com/…
  | 'WORKDAY'             // *.myworkdayjobs.com or apply.workday.com
  | 'DOVER'               // app.dover.com/…
  | 'JOBVITE'             // jobs.jobvite.com/… or apply.jobvite.com/…
  | 'BREEZY'              // company.breezy.hr/…
  | 'REMOTIVE_REDIRECT'   // remotive.com listing — must follow redirect
  | 'HIMALAYAS_REDIRECT'  // himalayas.app listing — must follow redirect
  | 'ARBEITNOW_REDIRECT'  // arbeitnow.com listing — must follow redirect
  | 'GENERIC';            // fallback

export interface PlatformDetectionResult {
  platform: PlatformType;
  confidence: 'high' | 'low';
}

/**
 * Detect the ATS platform from the URL alone (fast path, no network).
 * Returns null if URL pattern is ambiguous.
 */
export function detectPlatformFromUrl(url: string): PlatformDetectionResult | null {
  const u = url.toLowerCase();

  // High-confidence URL patterns
  if (u.includes('boards.greenhouse.io'))   return { platform: 'GREENHOUSE_V2',       confidence: 'high' };
  if (u.includes('gh_jid='))               return { platform: 'GREENHOUSE_EMBEDDED',  confidence: 'high' };
  if (u.includes('jobs.lever.co'))         return { platform: 'LEVER',               confidence: 'high' };
  if (u.includes('jobs.ashbyhq.com'))      return { platform: 'ASHBY',               confidence: 'high' };
  if (u.includes('myworkdayjobs.com'))     return { platform: 'WORKDAY',             confidence: 'high' };
  if (u.includes('apply.workday.com'))     return { platform: 'WORKDAY',             confidence: 'high' };
  if (u.includes('app.dover.com'))         return { platform: 'DOVER',               confidence: 'high' };
  if (u.includes('jobs.jobvite.com') ||
      u.includes('apply.jobvite.com'))     return { platform: 'JOBVITE',             confidence: 'high' };
  if (u.includes('.breezy.hr'))            return { platform: 'BREEZY',              confidence: 'high' };

  // Aggregator listing pages — need to follow apply link
  if (u.includes('remotive.com/remote-jobs')) return { platform: 'REMOTIVE_REDIRECT',  confidence: 'high' };
  if (u.includes('himalayas.app/jobs'))       return { platform: 'HIMALAYAS_REDIRECT', confidence: 'high' };
  if (u.includes('arbeitnow.com/jobs'))       return { platform: 'ARBEITNOW_REDIRECT', confidence: 'high' };

  return null;
}

/**
 * Inspect live page DOM for ATS fingerprints (slow path, used when URL is ambiguous).
 */
export async function detectPlatformFromDom(page: Page): Promise<PlatformDetectionResult> {
  try {
    // Check meta generator tags and known DOM markers
    const bodyText = await page.innerText('body').catch(() => '');
    const title = await page.title().catch(() => '');
    const currentUrl = page.url().toLowerCase();

    // Re-check URL after any redirects
    const fromUrl = detectPlatformFromUrl(currentUrl);
    if (fromUrl) return fromUrl;

    // DOM fingerprints
    const hasGreenhouseForm = (await page.locator('#application_form, .application-form, form[action*="greenhouse"]').count()) > 0;
    if (hasGreenhouseForm) return { platform: 'GREENHOUSE_V2', confidence: 'high' };

    const hasLeverForm = (await page.locator('.lever-job-posting, [class*="lever-"], form[action*="lever"]').count()) > 0;
    if (hasLeverForm) return { platform: 'LEVER', confidence: 'high' };

    const hasAshby = (await page.locator('[data-ashby-form], .ashby-application').count()) > 0;
    if (hasAshby) return { platform: 'ASHBY', confidence: 'high' };

    const hasWorkday = (await page.locator('[data-automation-id], [data-uxi-widget-type]').count()) > 5;
    if (hasWorkday) return { platform: 'WORKDAY', confidence: 'low' };

    // Title / body text heuristics
    if (/workday/i.test(title)) return { platform: 'WORKDAY', confidence: 'low' };
    if (/greenhouse/i.test(bodyText)) return { platform: 'GREENHOUSE_V2', confidence: 'low' };
    if (/lever/i.test(bodyText)) return { platform: 'LEVER', confidence: 'low' };

    return { platform: 'GENERIC', confidence: 'low' };
  } catch {
    return { platform: 'GENERIC', confidence: 'low' };
  }
}

/**
 * Full detection: URL first (fast), then DOM (slow) if needed.
 */
export async function detectPlatform(url: string, page: Page): Promise<PlatformDetectionResult> {
  const fromUrl = detectPlatformFromUrl(url);
  if (fromUrl && fromUrl.confidence === 'high') return fromUrl;
  return detectPlatformFromDom(page);
}
