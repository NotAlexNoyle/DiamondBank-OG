package net.trueog.diamondbankog

import java.io.File
import kotlin.properties.Delegates
import org.bukkit.configuration.file.YamlConfiguration

object Config {
    lateinit var prefix: String
    lateinit var postgresUrl: String
    lateinit var postgresUser: String
    lateinit var postgresPassword: String
    lateinit var postgresTable: String
    lateinit var postgresLogTable: String
    var postgresLogLimit by Delegates.notNull<Int>()
    lateinit var redisUrl: String

    /** @return True if failed */
    fun load(): Boolean {
        val file = File(DiamondBankOG.plugin.dataFolder, "config.yml")
        if (!file.exists()) {
            DiamondBankOG.plugin.saveDefaultConfig()
        }
        val config = YamlConfiguration.loadConfiguration(file)
        config.save(file)

        try {
            prefix = config.get("prefix") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"prefix\" as a string")
            return true
        }

        try {
            postgresUrl = config.get("postgresUrl") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"postgresUrl\" as a string")
            return true
        }

        try {
            postgresUser = config.get("postgresUser") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"postgresUser\" as a string")
            return true
        }

        try {
            postgresPassword = config.get("postgresPassword") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"postgresPassword\" as a string")
            return true
        }

        try {
            postgresTable = config.get("postgresTable") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"postgresTable\" as a string")
            return true
        }

        try {
            postgresLogTable = config.get("postgresLogTable") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"postgresLogTable\" as a string")
            return true
        }

        try {
            postgresLogLimit = config.get("postgresLogLimit") as Int
            if (postgresLogLimit < 1) {
                DiamondBankOG.plugin.logger.severe("\"postgresLogLimit\" cannot be less than 1")
                return true
            }
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe(
                "Failed to parse config option \"postgresLogLimit\" as an unsigned integer"
            )
            return true
        }

        try {
            redisUrl = config.get("redisUrl") as String
        } catch (_: Exception) {
            DiamondBankOG.plugin.logger.severe("Failed to parse config option \"redisUrl\" as a string")
            return true
        }

        return false
    }
}
