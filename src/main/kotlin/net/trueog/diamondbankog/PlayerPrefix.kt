package net.trueog.diamondbankog

import java.util.*
import kotlinx.coroutines.future.await

object PlayerPrefix {
    private val legacyToMmMap =
        mapOf(
            "0" to "<black>",
            "1" to "<dark_blue>",
            "2" to "<dark_green>",
            "3" to "<dark_aqua>",
            "4" to "<dark_red>",
            "5" to "<dark_purple>",
            "6" to "<gold>",
            "7" to "<gray>",
            "8" to "<dark_gray>",
            "9" to "<blue>",
            "a" to "<green>",
            "b" to "<aqua>",
            "c" to "<red>",
            "d" to "<light_purple>",
            "e" to "<yellow>",
            "f" to "<white>",
            "k" to "<obfuscated>",
            "l" to "<bold>",
            "m" to "<strikethrough>",
            "n" to "<underlined>",
            "o" to "<italic>",
            "r" to "<reset>",
            "*" to "<rainbow>",
        )

    val legacyRegex = Regex("[§&]([0-9a-fk-or*])", RegexOption.IGNORE_CASE)

    fun legacyToMm(text: String): String {
        return legacyRegex.replace(text) { legacyToMmMap[it.groupValues[1].lowercase()] ?: it.value }
    }

    suspend fun getPrefix(uuid: UUID): String {
        val user =
            DiamondBankOG.luckPerms.userManager.getUser(uuid)
                ?: DiamondBankOG.luckPerms.userManager.loadUser(uuid).await()
        val prefixNode = user.nodes.singleOrNull { node -> node.key.startsWith("prefix.1.") }
        if (prefixNode == null) return ""
        return legacyToMm(prefixNode.key.split(".").last()) + " "
    }
}
