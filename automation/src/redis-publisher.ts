import Redis from 'ioredis';
import { AutomationResult } from './types';

// ─── Shared Redis client ──────────────────────────────────────────────────────

const REDIS_HOST = process.env.REDIS_HOST ?? 'localhost';
const REDIS_PORT = parseInt(process.env.REDIS_PORT ?? '6379', 10);
const RESULTS_STREAM = 'cc:automation:results';

export const redisClient = new Redis({
  host: REDIS_HOST,
  port: REDIS_PORT,
  lazyConnect: true,
  retryStrategy: (times: number) => Math.min(times * 200, 5000),
});

redisClient.on('connect', () =>
  console.log(`[${new Date().toISOString()}] [redis-publisher] Connected to Redis ${REDIS_HOST}:${REDIS_PORT}`),
);
redisClient.on('error', (err: Error) =>
  console.error(`[${new Date().toISOString()}] [redis-publisher] Redis error:`, err),
);

// ─── Publish Result ───────────────────────────────────────────────────────────

/**
 * Serialize an AutomationResult and publish it to the cc:automation:results stream.
 * Arrays are JSON-stringified; all other values become strings.
 */
export async function publishResult(result: AutomationResult): Promise<void> {
  const ts = new Date().toISOString();
  console.log(`[${ts}] [redis-publisher] Publishing result for applicationId=${result.applicationId} status=${result.status}`);

  // XADD requires flat key-value pairs
  const fields: string[] = [
    'applicationId', result.applicationId,
    'status',        result.status,
    'fieldsFilled',  JSON.stringify(result.fieldsFilled),
    'unsupportedFields', JSON.stringify(result.unsupportedFields),
    'screenshotPath', result.screenshotPath ?? '',
    'platformResponse', result.platformResponse ?? '',
    'logs',          JSON.stringify(result.logs),
    'publishedAt',   ts,
  ];

  await redisClient.xadd(RESULTS_STREAM, '*', ...fields);
  console.log(`[${new Date().toISOString()}] [redis-publisher] Result published to stream ${RESULTS_STREAM}`);
}
