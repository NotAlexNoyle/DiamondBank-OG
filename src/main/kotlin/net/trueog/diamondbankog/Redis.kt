package net.trueog.diamondbankog

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionException

class Redis {
    private val redisClient: RedisClient = RedisClient.create(Config.redisUrl)

    /** @return True if failed */
    fun testConnection(): Boolean {
        try {
            val connection = redisClient.connect()
            connection.close()
            return false
        } catch (_: RedisConnectionException) {
            return true
        }
    }

    fun getValue(key: String): String? {
        val connection = redisClient.connect()
        val commands = connection.sync()
        val value = commands.get(key)
        connection.close()
        return value
    }

    fun setValue(key: String, value: String) {
        val connection = redisClient.connect()
        val commands = connection.sync()
        commands.set(key, value)
        connection.close()
    }

    fun shutdown() {
        redisClient.shutdown()
    }
}
