package net.trueog.diamondbankog

import java.util.*
import net.trueog.diamondbankog.PostgreSQL.PlayerShards

object ErrorHandler {
    class EconomyException(message: String) : Exception(message)

    /** Handles the error by throwing, disables the economy unless you specify it shouldn't */
    fun handleError(
        uuid: UUID,
        expectedMutatedShards: Int,
        playerShards: PlayerShards?,
        otherUuid: UUID? = null,
        dontDisableEconomy: Boolean = false,
    ) {
        if (!dontDisableEconomy) DiamondBankOG.economyDisabled = true

        throw EconomyException(
            """

            Player UUID: $uuid
            ${
                if (otherUuid != null) "Other Player UUID: $otherUuid" else ""
            }Expected Mutated Shards = $expectedMutatedShards${
                if (playerShards != null) {
                    if (playerShards.shardsInBank != -1) "Player Bank Balance: ${playerShards.shardsInBank}" else ""
                } else ""
            }${
                if (playerShards != null) {
                    if (playerShards.shardsInInventory != -1) "Player Inventory Balance: ${playerShards.shardsInInventory}" else ""
                } else ""
            }${
                if (playerShards != null) {
                    if (playerShards.shardsInEnderChest != -1) "Player Ender Chest Balance: ${playerShards.shardsInEnderChest}" else ""
                } else ""
            }
        """
                .trimIndent()
        )
    }
}
