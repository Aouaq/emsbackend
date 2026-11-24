-- Which sites the seeder has already had its say about, so it never speaks twice.
--
-- Until now the absence of an alarm_thresholds row was the seeder's only signal, and a
-- deliberate reset produces exactly that state. An operator clearing a site back to the
-- fleet defaults through the admin page would find the configured per-site limits
-- reinstated on the next restart, with nothing on screen to say why - and the restart is
-- the one moment nobody is watching.
--
-- The two facts are genuinely different: an alarm_thresholds row says what the limits
-- are now, and a row here says the configuration file has already been applied. Only the
-- second survives a reset, which is what lets a reset mean something permanent.
--
-- Cascades with the access key, because a marker for a site that no longer exists is
-- meaningless.
CREATE TABLE alarm_threshold_seeds (
    access_key_id BIGINT PRIMARY KEY REFERENCES access_keys (id) ON DELETE CASCADE,
    seeded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
