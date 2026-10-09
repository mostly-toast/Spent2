package com.aditya.spent.sms

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
data class SenderEntry(
    val header: String,
    val name: String,
    val kind: String,
    val status: String,
    val source: String,
)

@Serializable
private data class SenderFile(
    val senders: List<SenderEntry>,
    val generated_from: String? = null,
    val count: Int = 0,
)

class SenderRegistry(entries: List<SenderEntry>) {
    val entries: List<SenderEntry> = entries.distinctBy { it.header.trim().uppercase(Locale.ROOT) }

    private val byHeader: Map<String, SenderEntry> =
        entries.associateBy { it.header.trim().uppercase(Locale.ROOT) }

    val size: Int get() = byHeader.size

    fun entryFor(headers: List<String>): SenderEntry? =
        headers.firstNotNullOfOrNull { byHeader[it.trim().uppercase(Locale.ROOT)] }

    fun bankFor(headers: List<String>): String? = entryFor(headers)?.name

    fun isKnown(headers: List<String>): Boolean = entryFor(headers) != null

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun bundled(): SenderRegistry {
            val stream = SenderRegistry::class.java.classLoader.getResourceAsStream(RESOURCE)
                ?: error("Bundled sender registry $RESOURCE is missing")
            val text = stream.use { it.readBytes().decodeToString() }
            val file = runCatching { json.decodeFromString<SenderFile>(text) }
                .getOrElse { error("Bundled sender registry $RESOURCE is malformed: ${it.message}") }
            return SenderRegistry(file.senders)
        }

        private const val RESOURCE = "senders.json"
    }
}