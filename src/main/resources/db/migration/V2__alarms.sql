-- Per-site alarm limits.
--
-- Keyed to the access key rather than to a user: the limits belong to the installation,
-- not to whoever happens to be looking at it. A site with no row here uses the
-- application defaults, so adding a site needs no configuration to start being watched.
CREATE TABLE alarm_thresholds (
    access_key_id BIGINT PRIMARY KEY REFERENCES access_keys (id) ON DELETE CASCADE,
    max_power     DOUBLE PRECISION,
    min_voltage   DOUBLE PRECISION,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per crossing: raised when a reading passed a limit, cleared when it came back.
--
-- An interval rather than an event, because that is what the latch encodes - one record
-- on the way out and the same record closed on the way back, with nothing in between.
-- Storing it this way lets a client show "still active" and "lasted 40 minutes" without
-- inferring either from a stream of points.
CREATE TABLE alarms (
    id            BIGSERIAL PRIMARY KEY,
    access_key_id BIGINT           NOT NULL REFERENCES access_keys (id) ON DELETE CASCADE,
    kind          VARCHAR(32)      NOT NULL,
    raised_at     TIMESTAMPTZ      NOT NULL DEFAULT now(),
    cleared_at    TIMESTAMPTZ,
    value         DOUBLE PRECISION NOT NULL,
    threshold     DOUBLE PRECISION NOT NULL
);

-- The latch, enforced by the database rather than only by the poller.
--
-- At most one open alarm per site and kind. The poller already checks before inserting,
-- but two overlapping ticks - a slow Emoncms response, a redeploy mid-cycle - would both
-- see no open row and both insert. This makes that a constraint violation instead of a
-- duplicate the user has to read past.
CREATE UNIQUE INDEX uq_alarms_open ON alarms (access_key_id, kind) WHERE cleared_at IS NULL;

-- Listing a user's alarms newest first is the other query that runs constantly.
CREATE INDEX idx_alarms_raised_at ON alarms (raised_at DESC);
