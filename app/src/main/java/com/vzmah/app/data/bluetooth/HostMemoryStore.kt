package com.vzmah.app.data.bluetooth

import android.content.SharedPreferences

data class RememberedHost(
    val address: String,
    val name: String,
    val lastUsedAtMs: Long,
)

class HostMemoryStore(private val prefs: SharedPreferences) {

    fun remember(address: String, name: String, nowMs: Long) {
        if (address.isBlank()) return
        val rest = all().filterNot { it.address == address }
        save(rest + RememberedHost(address = address, name = name, lastUsedAtMs = nowMs))
    }

    fun all(): List<RememberedHost> {
        val raw = prefs.getString(KEY_HOSTS, null).orEmpty()
        if (raw.isEmpty()) return emptyList()
        return decode(raw).sortedByDescending { it.lastUsedAtMs }
    }

    fun lastUsed(): RememberedHost? = all().firstOrNull()

    fun isRemembered(address: String): Boolean = all().any { it.address == address }

    fun forget(address: String) {
        val rest = all().filterNot { it.address == address }
        if (rest.size == all().size) return
        save(rest)
    }

    private fun save(entries: List<RememberedHost>) {
        prefs.edit().putString(KEY_HOSTS, encode(entries)).apply()
    }

    companion object {
        private const val KEY_HOSTS = "remembered_hosts"

        private const val ESCAPE = '\\'
        private const val FIELD = '\u001F'
        private const val RECORD = '\u001E'

        private const val MAX_HOSTS = 12

        internal fun encode(entries: List<RememberedHost>): String = entries
            .sortedByDescending { it.lastUsedAtMs }
            .take(MAX_HOSTS)
            .joinToString(RECORD.toString()) { host ->
                listOf(escape(host.address), escape(host.name), host.lastUsedAtMs.toString())
                    .joinToString(FIELD.toString())
            }

        internal fun decode(raw: String): List<RememberedHost> = raw
            .split(RECORD)
            .mapNotNull { record ->
                val parts = record.split(FIELD)
                if (parts.size < 3) return@mapNotNull null
                val address = unescape(parts[0])
                val name = unescape(parts[1])
                val timestamp = parts[2].toLongOrNull() ?: return@mapNotNull null
                if (address.isBlank()) return@mapNotNull null
                RememberedHost(address = address, name = name, lastUsedAtMs = timestamp)
            }

        private fun escape(raw: String): String {
            val out = StringBuilder(raw.length)
            raw.forEach { char ->
                out.append(
                    when (char) {
                        ESCAPE -> "$ESCAPE" + "e"
                        FIELD -> "$ESCAPE" + "f"
                        RECORD -> "$ESCAPE" + "r"
                        else -> char.toString()
                    },
                )
            }
            return out.toString()
        }

        private fun unescape(raw: String): String {
            val out = StringBuilder(raw.length)
            var index = 0
            while (index < raw.length) {
                val char = raw[index]
                val next = raw.getOrNull(index + 1)
                if (char == ESCAPE && next != null) {
                    when (next) {
                        'e' -> { out.append(ESCAPE); index += 2 }
                        'f' -> { out.append(FIELD); index += 2 }
                        'r' -> { out.append(RECORD); index += 2 }

                        else -> { out.append(char); index++ }
                    }
                } else {
                    out.append(char)
                    index++
                }
            }
            return out.toString()
        }
    }
}
