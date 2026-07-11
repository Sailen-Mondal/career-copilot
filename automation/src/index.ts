import { startConsuming, closeConsumer } from './redis-consumer';
import { redisClient as publisherClient } from './redis-publisher';

function ts(): string {
  return `[${new Date().toISOString()}]`;
}

async function main(): Promise<void> {
  console.log(`${ts()} ══════════════════════════════════════════════════`);
  console.log(`${ts()} Career Copilot — Playwright Automation Worker`);
  console.log(`${ts()} PID        : ${process.pid}`);
  console.log(`${ts()} Redis host : ${process.env.REDIS_HOST ?? 'localhost'}:${process.env.REDIS_PORT ?? '6379'}`);
  console.log(`${ts()} Node       : ${process.version}`);
  console.log(`${ts()} Shadow mode: submit buttons will NEVER be clicked`);
  console.log(`${ts()} ══════════════════════════════════════════════════`);

  // ── Graceful shutdown ───────────────────────────────────────────────────
  async function shutdown(signal: string): Promise<void> {
    console.log(`\n${ts()} Received ${signal} — shutting down gracefully…`);
    process.exitCode = 0; // signals the consumer loop to stop

    try {
      await closeConsumer();
      console.log(`${ts()} Consumer connection closed.`);
    } catch (err) {
      console.error(`${ts()} Error closing consumer:`, err);
    }

    try {
      await publisherClient.quit();
      console.log(`${ts()} Publisher connection closed.`);
    } catch (err) {
      console.error(`${ts()} Error closing publisher:`, err);
    }

    console.log(`${ts()} Shutdown complete.`);
    process.exit(0);
  }

  process.on('SIGTERM', () => void shutdown('SIGTERM'));
  process.on('SIGINT',  () => void shutdown('SIGINT'));

  // ── Start consuming ─────────────────────────────────────────────────────
  try {
    await startConsuming();
  } catch (err) {
    console.error(`${ts()} [index] Unhandled error in consumer:`, err);
    process.exit(1);
  }

  console.log(`${ts()} Worker exited.`);
}

main().catch(err => {
  console.error(`[${new Date().toISOString()}] [index] Fatal startup error:`, err);
  process.exit(1);
});
