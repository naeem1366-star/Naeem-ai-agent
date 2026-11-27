import "dotenv/config";
import Fastify from "fastify";

const app = Fastify({
  logger: {
    level: process.env.NODE_ENV === "production" ? "info" : "debug",
    redact: [
      "req.headers.authorization",
      "req.headers.cookie",
      "req.body.activationCode",
      "req.body.apiKey",
      "req.body.prompt",
    ],
  },
  bodyLimit: 1_500_000,
});

app.get("/health", async () => ({
  ok: true,
  service: "ema-core",
  status: "scaffold",
}));

const host = process.env.HOST ?? "127.0.0.1";
const port = Number(process.env.PORT ?? 8787);

try {
  await app.listen({ host, port });
} catch (error) {
  app.log.error(error);
  process.exit(1);
}