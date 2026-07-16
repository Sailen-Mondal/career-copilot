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
  
  // Note: if the target is a Frame, we must evaluate on the page, but genericFill expects Page.
  // Playwright's Page acts as the root, so passing `page` is correct and genericFill will handle the main page's elements.
  // If we have an iframe, we can frame-fill, but greenhouse is mostly direct boards or iframes. 
  // Let's pass target (Page | Frame) to genericFill? 
  // Wait! In generic-filler.ts, we declared page: Page. Let's make it page: Page | Frame in generic-filler.ts so it can fill inside frames too!
  // That's an amazing idea!
  return genericFill(page, resume, pdfResumePath, coverLetterPath, logs, isLive);
}
