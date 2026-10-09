package com.aditya.spent.repository

interface SenderMappingRepository {
    suspend fun accountIdForSender(header: String): String?
    suspend fun mapSenderToAccount(header: String, accountId: String)
    suspend fun isSenderIgnored(header: String): Boolean
    suspend fun ignoreSender(header: String)
}