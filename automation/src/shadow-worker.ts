// shadow-worker.ts — orchestrates a single application run.
// All form-filling logic lives in fillers/; platform detection in platform-detector.ts;
// PDF generation in resume-writer.ts.
// ponytail: this file is now ~150 lines vs the original ~620. Same outcome.

import { chromium, Page, BrowserContext } from 'playwright';
import * as fs from 'fs';
import * as path from 'path';
import { AutomationCommand, AutomationResult } from './types';
import { redisClient, publishResult } from './redis-publisher';
import { detectPlatform } from './platform-detector';
import { generateResumePdf, ResumeData } from './resume-writer';
import { fillWithStrategy, ResumeProfile } from './fillers/index';

// ─── Constants ────────────────────────────────────────────────────────────────

const SCREENSHOTS_DIR = path.resolve(process.cwd(), 'screenshots');
const NAVIGATION_TIMEOUT_MS = 30_000;

// ─── Helpers ─────────────────────────────────────────────────────────────────

function ts(): string { return `[${new Date().toISOString()}]`; }
function log(msg: string): string { const line = `${ts()} ${msg}`; console.log(line); return line; }
function ensureScreenshotsDir(): void { if (!fs.existsSync(SCREENSHOTS_DIR)) fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true }); }

function buildResumeProfile(command: AutomationCommand): ResumeProfile {
  const fullName  = command.candidateName || 'Candidate';
  const parts     = fullName.split(' ');
  let customAnswers: Record<string, string> = {};
  if (command.customAnswersJson) {
    try {
      customAnswers = JSON.parse(command.customAnswersJson);
    } catch (e) {
      console.error('Failed to parse customAnswersJson:', e);
    }
  }
  return {
    name:        fullName,
    firstName:   parts[0] || 'Candidate',
    lastName:    parts.slice(1).join(' ') || 'Name',
    fullName,
    email:       command.candidateEmail    || '',
    phone:       command.candidatePhone    || '',
    address:     'India',
    location:    'Kolkata, West Bengal, India',
    linkedIn:    command.candidateLinkedin || '',
    website:     command.candidateWebsite  || '',
    coverLetter: command.coverLetterContent || '',
    summary:     command.resumeContent     || '',
    customAnswers,
    applicationId: command.applicationId,
  };
}

function buildResumeData(command: AutomationCommand): ResumeData {
  return {
    name:          command.candidateName  || 'Candidate',
    email:         command.candidateEmail || '',
    phone:         command.candidatePhone || '',
    linkedIn:      command.candidateLinkedin || '',
    website:       command.candidateWebsite  || '',
    resumeContent: command.resumeContent     || '',
  };
}

// ─── Main Worker ─────────────────────────────────────────────────────────────

export async function runShadowCommand(command: AutomationCommand): Promise<AutomationResult> {
  const logs: string[] = [];
  const isLive = command.mode === 'live';

  logs.push(log(`applicationId=${command.applicationId} url=${command.jobUrl} mode=${command.mode}`));
  ensureScreenshotsDir();

  const screenshotPath = path.join(SCREENSHOTS_DIR, `${command.applicationId}.png`);
  const resume         = buildResumeProfile(command);
  const resumeData     = buildResumeData(command);

  // ── Prepare temp files ────────────────────────────────────────────────────
  const pdfResumePath     = path.resolve(process.cwd(), `resume_${command.applicationId}.pdf`);
  const coverLetterPath   = path.resolve(process.cwd(), `cover_letter_${command.applicationId}.txt`);
  const tempFiles         = [pdfResumePath, coverLetterPath];

  fs.writeFileSync(coverLetterPath, command.coverLetterContent || '');

  // Generate professional PDF resume (LaTeX → latexonline.cc, fallback pdf-lib)
  await generateResumePdf(resumeData, pdfResumePath, (msg) => logs.push(log(msg)));

  let browser = null;
  let context: BrowserContext | null = null;

  try {
    logs.push(log('Launching Chromium headless…'));
    browser = await chromium.launch({
      headless: true,
      slowMo: 30, // light human simulation — helps with bot detection
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
    });

    context = await browser.newContext({
      userAgent:
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ' +
        'AppleWebKit/537.36 (KHTML, like Gecko) ' +
        'Chrome/131.0.0.0 Safari/537.36',
      viewport: { width: 1280, height: 900 },
    });

    const page: Page = await context.newPage();
    page.setDefaultTimeout(NAVIGATION_TIMEOUT_MS);

    // ── Navigate ────────────────────────────────────────────────────────────
    logs.push(log(`Navigating to ${command.jobUrl}`));
    await page.goto(command.jobUrl, { waitUntil: 'domcontentloaded', timeout: NAVIGATION_TIMEOUT_MS });
    await page.waitForTimeout(2000);
    logs.push(log(`Loaded: ${page.url()}`));

    // ── Detect platform ─────────────────────────────────────────────────────
    const detection = await detectPlatform(command.jobUrl, page);
    logs.push(log(`Platform detected: ${detection.platform} (confidence: ${detection.confidence})`));

    // ── Fill with appropriate strategy ──────────────────────────────────────
    const result = await fillWithStrategy(
      detection.platform,
      page,
      resume,
      pdfResumePath,
      coverLetterPath,
      logs,
      isLive,
    );

    logs.push(log(`Fields filled: ${result.fieldsFilled.length}, skipped: ${result.unsupportedFields.length}`));

    // ── Screenshot ──────────────────────────────────────────────────────────
    await page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => {});
    logs.push(log(`Screenshot → ${screenshotPath}`));

    // ── Determine final status ───────────────────────────────────────────────
    let finalStatus: AutomationResult['status'];
    if (!isLive) {
      finalStatus = 'shadow_completed';
    } else if (result.errorBody === 'SKIPPED_REQUIRES_ACCOUNT') {
      finalStatus = 'skipped';
    } else if (result.submitted) {
      finalStatus = 'submitted';
    } else {
      finalStatus = 'failed';
    }

    logs.push(log(`Done. status=${finalStatus}`));

    return {
      applicationId:  command.applicationId,
      status:         finalStatus,
      fieldsFilled:   result.fieldsFilled,
      unsupportedFields: result.unsupportedFields,
      screenshotPath,
      platformResponse: result.confirmationUrl,
      logs,
    };

  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    logs.push(log(`FATAL: ${message}`));
    console.error(`${ts()} [shadow-worker] Fatal for ${command.applicationId}:`, err);
    return {
      applicationId: command.applicationId,
      status: 'failed',
      fieldsFilled: [],
      unsupportedFields: [],
      screenshotPath: undefined,
      logs,
    };
  } finally {
    // Clean up temp files
    for (const f of tempFiles) {
      try { if (fs.existsSync(f)) fs.unlinkSync(f); } catch { /* ignore */ }
    }
    // Clean up .tex if left behind
    const texPath = pdfResumePath.replace(/\.pdf$/, '.tex');
    try { if (fs.existsSync(texPath)) fs.unlinkSync(texPath); } catch { /* ignore */ }

    if (context) { try { await context.close(); } catch { /* ignore */ } }
    if (browser)  { try { await browser.close();  } catch { /* ignore */ } logs.push(log('Browser closed.')); }
  }
}
