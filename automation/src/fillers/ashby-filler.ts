// Ashby Form Filler
// Handles jobs.ashbyhq.com/… — React SPA form.

import { Page } from 'playwright';
import { ResumeProfile, FillerResult } from './types';
import { genericFill } from './generic-filler';

export async function ashbyFill(
  page: Page,
  resume: ResumeProfile,
  pdfResumePath: string,
  coverLetterPath: string,
  logs: string[],
  isLive: boolean,
): Promise<FillerResult> {
  logs.push('[ashby] Starting Ashby filler');
  
  // Wait for the application form to mount
  try {
    await page.waitForSelector('form, [data-ashby-form], .ashby-application-form', { timeout: 15_000 });
  } catch {
    logs.push('[ashby] Form selector not found, attempting to fill anyway');
  }

  // Delegate actual form-filling to genericFill (中央 Brain)
  logs.push('[ashby] Delegating actual form filling to genericFill brain');
  return genericFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive);
}
