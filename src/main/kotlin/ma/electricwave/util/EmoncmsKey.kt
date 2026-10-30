package ma.electricwave.util

object EmoncmsKey {

    private val ALLOWED = Regex("^[A-Za-z0-9]{16,64}$")

    fun normalize(raw: String): String = raw.filterNot { it.isWhitespace() }

    fun isValid(raw: String): Boolean = ALLOWED.matches(normalize(raw))

    fun hint(raw: String): String = normalize(raw).take(4) + "\u2026"

    fun hash(raw: String): String = sha256Hex(normalize(raw))
}
