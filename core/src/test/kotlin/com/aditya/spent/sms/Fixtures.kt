package com.aditya.spent.sms

import com.aditya.spent.model.RawSms
import java.io.File

object Fixtures {
    private val headerLine = Regex("^---\\s*(.+?)\\s*\\[([A-Za-z0-9-]+)\\]\\s*$")
    private val pipeLine = Regex("^([^|]+)\\|(\\d+)\\|(.*)$")

    fun loadAll(): List<RawSms> {
        val root = smsDirectory() ?: return emptyList()
        return root.walk().filter { it.isFile && it.extension == "txt" }.sortedBy { it.path }.flatMap { file ->
            parse(file.readLines(), file.name)
        }.toList()
    }

    fun smsDirectory(): File? {
        val url = Fixtures::class.java.classLoader.getResource("sms")
        if (url != null && url.protocol == "file") {
            val directory = File(url.toURI())
            if (directory.isDirectory) return directory
        }
        val local = File("src/test/resources/sms")
        return if (local.isDirectory) local else null
    }

    private fun parse(lines: List<String>, name: String): List<RawSms> {
        val messages = mutableListOf<RawSms>()
        var pendingSender: String? = null
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val header = headerLine.matchEntire(trimmed)
            if (header != null) {
                pendingSender = header.groupValues[2]
                continue
            }
            val pipe = pipeLine.matchEntire(trimmed)
            val sender: String
            val timestamp: Long
            val body: String
            if (pipe != null) {
                sender = pipe.groupValues[1]
                timestamp = pipe.groupValues[2].toLong()
                body = pipe.groupValues[3]
            } else if (pendingSender != null) {
                sender = pendingSender
                timestamp = 0L
                body = trimmed
            } else {
                sender = lines.first().trim()
                timestamp = 0L
                body = lines.drop(1).joinToString("\n").trim()
            }
            messages += RawSms(id = "$name#${messages.size}", sender = sender, body = body, timestampMillis = timestamp)
        }
        return messages
    }
}