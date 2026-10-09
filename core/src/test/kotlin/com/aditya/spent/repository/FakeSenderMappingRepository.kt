package com.aditya.spent.repository

class FakeSenderMappingRepository : SenderMappingRepository {
    private val mappings = mutableMapOf<String, String>()
    private val ignored = mutableSetOf<String>()

    override suspend fun accountIdForSender(header: String): String? = mappings[header]

    override suspend fun mapSenderToAccount(header: String, accountId: String) {
        mappings[header] = accountId
    }

    override suspend fun isSenderIgnored(header: String): Boolean = header in ignored

    override suspend fun ignoreSender(header: String) {
        ignored += header
    }
}