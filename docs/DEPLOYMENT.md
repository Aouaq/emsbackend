# Deployment

The stack is `compose.prod.yaml`. It is a different file from `compose.yaml`, which is
for development and must not be deployed — it defaults the Spring profile back to `dev`
(booting on the published development JWT key, crypto master key and admin password),
hardcodes the database password, and publishes Postgres and Redis on every interface.

```bash
EMS_IMAGE=aouaq/emsbackend:1.0.1 \
  docker compose -f compose.prod.yaml --env-file .env.production up -d
```

`--env-file` is not optional. Compose reads two different things from two different
places: `${...}` in the compose file is substituted from the shell or `--env-file`,
while the `env_file:` entry inside the service is what reaches the container. Both need
`.env.production`.

`EMS_IMAGE` has no default, so every deployment names its version. `latest` makes "roll
back to what was working" a question nobody can answer.

## The trap that cost the first deployment a day

**`docker restart` does not reload `.env`.** Editing the environment file and restarting
gives you a container still running the old values — which is how a valid 32-byte
`APP_CRYPTO_MASTER_KEY` on disk sat beside an invalid one inside the container, and the
backend restart-looped against an error that correctly described a key the file no longer
contained.

Recreate instead:

```bash
docker compose -f compose.prod.yaml --env-file .env.production up -d --force-recreate app
```

To see what a running container actually has, ask the container, not the file:

```bash
docker compose -f compose.prod.yaml exec app printenv | grep APP_
```

## Ubuntu, AppArmor and Postgres

On Ubuntu hosts, AppArmor denies a signal `gosu` sends during first-run initialisation.
The entrypoint hangs, the database is never created, and the container sits "running but
unhealthy" over an empty volume — with nothing useful in its own logs. The giveaway is in
the kernel log:

```bash
sudo dmesg | grep -i apparmor | tail
```

`compose.prod.yaml` already carries `security_opt: ['apparmor:unconfined']` on the
postgres service, which exempts that one container rather than weakening the host policy.
Nothing further to do; this note is so the next person recognises the symptom.

## What the application checks for you at startup

Both already exist — a failure here is the application telling you something true:

- `CryptoService` rejects a blank `APP_CRYPTO_MASTER_KEY` outside `dev`, one that is not
  valid base64, and one that does not decode to exactly 32 bytes.
- `SecurityStartupValidator` refuses to start if `APP_JWT_SECRET`,
  `APP_CRYPTO_MASTER_KEY` or `APP_ADMIN_PASSWORD` is still a published development value.

**Never "fix" a crypto-key error by generating a new key.** Emoncms access keys in the
database are encrypted under the old one; a new key starts cleanly and then returns null
for every stored key. Recover the original.

## Health

```
/actuator/health            aggregate
/actuator/health/readiness  serving traffic
/actuator/health/liveness   process is alive
```

Readiness is what the image's `HEALTHCHECK` and the compose healthcheck use. The
aggregate folds in the SMTP probe, so where mail is blocked it reports the whole service
down after a 20-second timeout — a mail outage read as a dead instance is how a restart
loop begins. `APP_MAIL_HEALTH_ENABLED=true` restores that probe where the relay is
reachable.

## Reverse proxy

The app publishes to `127.0.0.1:8082` only. Terminate TLS in front of it and proxy to
that address. The proxy must set `X-Forwarded-For`, and `APP_TRUSTED_PROXIES` must name
it — otherwise every request appears to come from the proxy and the rate limiter buckets
the entire internet together.

## Rolling back

Images are versioned, so a rollback is the previous tag:

```bash
EMS_IMAGE=aouaq/emsbackend:1.0.0 \
  docker compose -f compose.prod.yaml --env-file .env.production up -d app
docker compose -f compose.prod.yaml ps          # wait for (healthy)
```

Flyway migrations are forward-only. Rolling the image back does **not** roll the schema
back, so a release containing a destructive migration is not safely reversible this way —
check `src/main/resources/db/migration` before relying on it.

## Backups

Both volumes matter, for different reasons.

`ems-postgres-data` is the system of record:

```bash
docker compose -f compose.prod.yaml exec -T postgres \
  pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "ems-$(date +%F).sql.gz"
```

Restoring is only half the job — the dump's encrypted Emoncms keys are readable only with
the `APP_CRYPTO_MASTER_KEY` that was current when they were written. Back up that key
with the dump, somewhere else.

`ems-redis-data` holds refresh tokens. It does not need backing up — losing it signs
everyone out, which is recoverable — but it does need to **persist across restarts**, which
is why the service runs with `--appendonly yes` over a named volume. Without both, every
restart ends every session on the fleet.

## Memory

Roughly 1.3 GB of a 2 GB box: 768m app, 384m postgres, 128m redis. The JVM sizes its heap
from the container limit (`-XX:MaxRAMPercentage=65` in the image), so `mem_limit` on the
app service is the one number that governs it. Watch with:

```bash
docker stats --no-stream
```

If the app is killed with exit code 137 it hit the limit; raise `mem_limit` rather than
the percentage, so the JVM and the cgroup keep agreeing.
