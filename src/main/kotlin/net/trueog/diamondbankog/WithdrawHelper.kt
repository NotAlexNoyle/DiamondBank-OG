package net.trueog.diamondbankog

import kotlin.math.floor
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.InventoryExtensions.withdraw
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.entity.Player

object WithdrawHelper {
    /** @return The amount of not removed shards, -1 if error */
    suspend fun withdrawFromPlayer(player: Player, shards: Int): Int {
        val playerShards = DiamondBankOG.postgreSQL.getPlayerShards(player.uniqueId, ShardType.ALL)
        if (
            playerShards.shardsInBank == null ||
                playerShards.shardsInInventory == null ||
                playerShards.shardsInEnderChest == null
        ) {
            return -1
        }

        // Withdraw everything
        if (shards == -1) {
            val error =
                DiamondBankOG.postgreSQL.subtractFromPlayerShards(
                    player.uniqueId,
                    playerShards.shardsInBank,
                    ShardType.BANK,
                )
            if (error) {
                handleError(player.uniqueId, shards, playerShards)
                player.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                    )
                )
                return -1
            }

            val notRemovedInventory = player.inventory.withdraw(playerShards.shardsInInventory)

            val notRemovedEnderChest = player.enderChest.withdraw(playerShards.shardsInEnderChest)
            return notRemovedInventory + notRemovedEnderChest
        }

        if (shards > playerShards.shardsInBank + playerShards.shardsInInventory + playerShards.shardsInEnderChest) {
            val diamonds = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
            val totalDiamonds =
                String.format(
                    "%.1f",
                    floor(
                        ((playerShards.shardsInBank +
                            playerShards.shardsInInventory +
                            playerShards.shardsInEnderChest) / 9.0) * 10
                    ) / 10.0,
                )
            player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${Config.prefix}<reset>: <red>Cannot use <yellow>$diamonds <aqua>Diamond${if (diamonds != "1.0") "s" else ""} <red>in a transaction because you only have <yellow>$totalDiamonds <aqua>Diamond${if (totalDiamonds != "1.0") "s" else ""}<red>."
                )
            )
            return -1
        }

        if (shards <= playerShards.shardsInBank) {
            val error = DiamondBankOG.postgreSQL.subtractFromPlayerShards(player.uniqueId, shards, ShardType.BANK)
            if (error) {
                handleError(player.uniqueId, shards, playerShards)
                player.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                    )
                )
                return -1
            }
            return 0
        }

        if (shards <= playerShards.shardsInBank + playerShards.shardsInInventory) {
            val error =
                DiamondBankOG.postgreSQL.subtractFromPlayerShards(
                    player.uniqueId,
                    playerShards.shardsInBank,
                    ShardType.BANK,
                )
            if (error) {
                handleError(player.uniqueId, shards, playerShards)
                player.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                    )
                )
                return -1
            }

            val notRemoved = player.inventory.withdraw(shards - playerShards.shardsInBank)
            return notRemoved
        }

        val error =
            DiamondBankOG.postgreSQL.subtractFromPlayerShards(
                player.uniqueId,
                playerShards.shardsInBank,
                ShardType.BANK,
            )
        if (error) {
            handleError(player.uniqueId, shards, playerShards)
            player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                )
            )
            return -1
        }

        val notRemovedInventory = player.inventory.withdraw(playerShards.shardsInInventory)

        val notRemovedEnderChest =
            player.enderChest.withdraw(shards - (playerShards.shardsInBank + playerShards.shardsInInventory))

        return notRemovedInventory + notRemovedEnderChest
    }
}
