// Greenhouse V2 Form Filler
// Handles boards.greenhouse.io or embedded iframe boards.

import { Page, Frame } from 'playwright';
import { ResumeProfile, FillerResult } from './types';
import { genericFill } from './generic-filler';

export async function greenhouseFill(
  page: Page,
  resume: ResumeProfile,
  pdfResumePath: string,
  coverLetterPath: string,
  logs: string[],
  isLive: boolean,
): Promise<FillerResult> {
  logs.push('[greenhouse] Starting Greenhouse filler');

  // Detect embedded iframe if present
  let target: Page | Frame = page;
  const frames = page.frames();
  const greenhouseFrame = frames.find(f => f.url().includes('greenhouse.io'));
  if (greenhouseFrame) {
    logs.push(`[greenhouse] Found embedded iframe: ${greenhouseFrame.url()}`);
    target = greenhouseFrame;
  }

  // Delegate actual form-filling to genericFill (中央 Brain)
  logs.push('[greenhouse] Delegating actual form filling to genericFill brain');
  return genericFill(target, resume, pdfResumePath, coverLetterPath, logs, isLive);
}
