-- What one user calls a site, and where they say it is.
--
-- Keyed to the user *and* the access key, unlike alarm_thresholds, which hangs off the
-- key alone. Limits belong to the installation; a name does not. A site is shared by
-- everyone linked to it, and one technician's "Atelier nord" is another's "Kammah 3",
-- so a rename here can never change what a colleague sees.
--
-- Every column is nullable, and a missing row is the normal state. Reading falls back
-- to the account's description and then to its resource - exactly what the app showed
-- before this table existed, so it changes nothing until somebody edits something.
CREATE TABLE site_preferences (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    access_key_id  BIGINT NOT NULL REFERENCES access_keys (id) ON DELETE CASCADE,

    -- The label this user gave the site.
    name           VARCHAR(100),

    -- Location in two parts, as the monitoring app this is modelled on splits it: a
    -- picked region ("Marrakech,Marrakech-Safi,Morocco") and a free-text detail
    -- ("Targa"). Joined detail-first for display.
    region         VARCHAR(255),
    address_detail VARCHAR(255),

    -- The pin. Separate from the text because the text is what a person reads and these
    -- are what a map needs, and a site can have one without the other: an address typed
    -- by hand has no coordinates, and a dropped pin may never be reverse-geocoded.
    latitude       DOUBLE PRECISION,
    longitude      DOUBLE PRECISION,

    -- Object-store URL, not bytes. The instance has an ephemeral disk, so anything
    -- written beside the application is gone on the next deploy.
    image_url      VARCHAR(512),

    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per user per site. Without it, two PATCHes racing on a flaky connection leave
-- two rows and the read picks one of them arbitrarily.
CREATE UNIQUE INDEX uq_site_preferences_user_key
    ON site_preferences (user_id, access_key_id);

-- Every sync reads one user's whole set, which is the only query that runs constantly.
CREATE INDEX idx_site_preferences_user ON site_preferences (user_id);
