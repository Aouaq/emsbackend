package ma.electricwave.config

/**
 * The working secrets committed to `application-dev.yml`.
 *
 * They exist so a developer can clone and run without provisioning anything, and so
 * locally encrypted Emoncms keys survive a restart. They are published in the repository
 * and must therefore never reach a deployed environment: anyone holding
 * [JWT_SECRET] can mint an `ADMIN` access token, and anyone holding [CRYPTO_MASTER_KEY]
 * can decrypt every stored Emoncms key.
 *
 * [SecurityStartupValidator] refuses to start outside the `dev` profile when any of these
 * values is in use. Keep them byte-for-byte identical to `application-dev.yml`; a
 * mismatch silently turns that check into a no-op.
 */
object DevelopmentDefaults {

    const val JWT_SECRET = "dev-only-secret-change-me-with-at-least-32-bytes!!"

    const val CRYPTO_MASTER_KEY = "ZGV2LW9ubHktY3J5cHRvLW1hc3Rlci1rZXktMzJieXQ="

    const val ADMIN_PASSWORD = "admin123"
}
