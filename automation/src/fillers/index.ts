// Strategy router — picks the right filler based on detected platform.
// Single dispatch point; keeps shadow-worker.ts thin.

import { Page } from 'playwright';
import { PlatformType } from '../platform-detector';
import { ResumeProfile, FillerResult } from './types';

export type { ResumeProfile, FillerResult };

export async function fillWithStrategy(
  platform: PlatformType,
  page: Page,
  resume: ResumeProfile,
  pdfResumePath: string,
  coverLetterPath: string,
  logs: string[],
  isLive: boolean,
): Promise<FillerResult> {
  logs.push(`[router] Platform: ${platform}`);

  switch (platform) {
    case 'GREENHOUSE_V2':
    case 'GREENHOUSE_EMBEDDED': {
      const { greenhouseFill } = await import('./greenhouse-filler');
      return greenhouseFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive);
    }

    case 'LEVER': {
      const { leverFill } = await import('./lever-filler');
      return leverFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive);
    }

    case 'ASHBY': {
      const { ashbyFill } = await import('./ashby-filler');
      return ashbyFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive);
    }

    case 'REMOTIVE_REDIRECT': {
      const { aggregatorFill } = await import('./aggregator-filler');
      return aggregatorFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive, 'remotive');
    }

    case 'HIMALAYAS_REDIRECT': {
      const { aggregatorFill } = await import('./aggregator-filler');
      return aggregatorFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive, 'himalayas');
    }

    case 'ARBEITNOW_REDIRECT': {
      const { aggregatorFill } = await import('./aggregator-filler');
      return aggregatorFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive, 'arbeitnow');
    }

    case 'WORKDAY': {
      // Workday requires account — attempt guest apply, skip if wall hit
      logs.push('[router] WORKDAY: attempting guest apply path');
      const { genericFill } = await import('./generic-filler');
      const result = await genericFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive);
      if (!result.submitted && isLive) {
        logs.push('[router] WORKDAY: could not complete — marking as SKIPPED_REQUIRES_ACCOUNT');
        result.errorBody = 'SKIPPED_REQUIRES_ACCOUNT';
      }
      return result;
    }

    case 'DOVER':
    case 'JOBVITE':
    case 'BREEZY':
    case 'GENERIC':
    default: {
      const { genericFill } = await import('./generic-filler');
      return genericFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive);
    }
  }
}
