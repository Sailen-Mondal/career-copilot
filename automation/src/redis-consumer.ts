import Redis from 'ioredis';
import { AutomationCommand } from './types';
import { runShadowCommand } from './shadow-worker';
import { publishResult, redisClient as publisherClient } from './redis-publisher';

// ─── Config ──────────────────────────────────────────────────────────────────

const REDIS_HOST = process.env.REDIS_HOST ?? 'localhost';
const REDIS_PORT = parseInt(process.env.REDIS_PORT ?? '6379', 10);
const JOBS_STREAM = 'cc:automation:jobs';
const CONSUMER_GROUP = 'playwright-workers';
const CONSUMER_NAME = `worker-${process.pid}`;
const BLOCK_MS = 5000;

function ts(): string {
  return `[${new Date().toISOString()}]`;
}

function log(msg: string): void {
  console.log(`${ts()} [redis-consumer] ${msg}`);
}

// ─── Dedicated read client (XREADGROUP blocks) ────────────────────────────────

const readClient = new Redis({
  host: REDIS_HOST,
  port: REDIS_PORT,
  lazyConnect: true,
  retryStrategy: (times: number) => Math.min(times * 200, 5000),
});

readClient.on('connect', () =>
  log(`Connected to Redis ${REDIS_HOST}:${REDIS_PORT} (consumer: ${CONSUMER_NAME})`),
);
readClient.on('error', (err: Error) =>
  console.error(`${ts()} [redis-consumer] Redis error:`, err),
);

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** Parse a flat Redis stream field array into a key→value map. */
function parseStreamFields(fields: string[]): Record<string, string> {
  const map: Record<string, string> = {};
  for (let i = 0; i + 1 < fields.length; i += 2) {
    map[fields[i]] = fields[i + 1];
  }
  return map;
}

/**
 * Reconstruct an AutomationCommand from a flat stream field map.
 * Fields are sent as plain strings by the Java producer.
 */
function deserializeCommand(fields: Record<string, string>): AutomationCommand {
  return {
    applicationId: fields['applicationId'] ?? '',
    jobUrl: fields['jobUrl'] ?? '',
    mode: (fields['mode'] as AutomationCommand['mode']) ?? 'shadow',
    profileSnapshotId: fields['profileSnapshotId'] ?? '',
    resumeDocumentId: fields['resumeDocumentId'] ?? '',
    coverLetterDocumentId: fields['coverLetterDocumentId'] || undefined,
  };
}

// ─── Consumer Loop ────────────────────────────────────────────────────────────

export async function startConsuming(): Promise<void> {
  log('Connecting…');
  await readClient.connect();
  await publisherClient.connect();

  // Create consumer group, ignore BUSYGROUP error if it already exists
  try {
    await readClient.xgroup('CREATE', JOBS_STREAM, CONSUMER_GROUP, '$', 'MKSTREAM');
    log(`Consumer group "${CONSUMER_GROUP}" created (stream auto-created if needed).`);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    if (message.includes('BUSYGROUP')) {
      log(`Consumer group "${CONSUMER_GROUP}" already exists — continuing.`);
    } else {
      throw err;
    }
  }

  log(`Starting XREADGROUP loop on stream "${JOBS_STREAM}" …`);

  // Flag set by SIGTERM handler in index.ts
  while (!process.exitCode) {
    let rawMessages: Array<[string, Array<[string, string[]]>]> | null = null;

    try {
      // XREADGROUP GROUP <group> <consumer> COUNT 1 BLOCK <ms> STREAMS <stream> >
      rawMessages = (await readClient.xreadgroup(
        'GROUP', CONSUMER_GROUP, CONSUMER_NAME,
        'COUNT', '1',
        'BLOCK', String(BLOCK_MS),
        'STREAMS', JOBS_STREAM, '>',
      )) as Array<[string, Array<[string, string[]]>]> | null;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      // NOGROUP means the stream/group was deleted; stop gracefully
      if (msg.includes('NOGROUP')) {
        log('Stream or consumer group no longer exists — shutting down loop.');
        break;
      }
      log(`XREADGROUP error (will retry): ${msg}`);
      await new Promise(resolve => setTimeout(resolve, 1000));
      continue;
    }

    if (!rawMessages || rawMessages.length === 0) {
      // Timeout — loop again
      continue;
    }

    // rawMessages = [ [streamName, [ [messageId, [f1,v1,f2,v2,...]] ]] ]
    for (const [, messages] of rawMessages) {
      for (const [messageId, fieldArray] of messages) {
        log(`Received message id=${messageId}`);
        const fieldMap = parseStreamFields(fieldArray);

        // HALT sentinel — used in testing / graceful drain
        if ('HALT' in fieldMap) {
          log('HALT message received — acknowledging and stopping consumer loop.');
          await readClient.xack(JOBS_STREAM, CONSUMER_GROUP, messageId);
          process.exitCode = 0;
          return;
        }

        const command = deserializeCommand(fieldMap);
        log(`Processing applicationId=${command.applicationId} jobUrl=${command.jobUrl}`);

        try {
          const result = await runShadowCommand(command);
          await publishResult(result);
          log(`Result published for applicationId=${command.applicationId} status=${result.status}`);
        } catch (err) {
          const msg = err instanceof Error ? err.message : String(err);
          log(`ERROR processing ${command.applicationId}: ${msg}`);

          // Publish failure result so the upstream knows
          await publishResult({
            applicationId: command.applicationId,
            status: 'failed',
            fieldsFilled: [],
            unsupportedFields: [],
            logs: [`${ts()} FATAL: ${msg}`],
          }).catch(() => { /* best-effort */ });
        } finally {
          // Always ACK so the message is not redelivered
          try {
            await readClient.xack(JOBS_STREAM, CONSUMER_GROUP, messageId);
            log(`ACKed message id=${messageId}`);
          } catch (ackErr) {
            log(`WARN: Failed to ACK message id=${messageId}: ${ackErr}`);
          }
        }
      }
    }
  }

  log('Consumer loop exited.');
}

/** Cleanly close the read client (called from index.ts on SIGTERM). */
export async function closeConsumer(): Promise<void> {
  await readClient.quit().catch(() => { /* ignore */ });
}
