package net.trueog.diamondbankog

import kotlinx.coroutines.launch
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.entity.Player

object AutoDeposit {
    fun deposit(player: Player, item: Item) {
        val worldName = player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") {
            return
        }

        if (!player.hasPermission("diamondbank-og.deposit")) {
            player.sendMessage(
                DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You do not have permission to deposit.")
            )
            return
        }

        val itemStack = item.itemStack
        val shards =
            if (itemStack.type == Material.DIAMOND_BLOCK) {
                itemStack.amount * 9 * 9
            } else if (itemStack.type == Material.DIAMOND) {
                itemStack.amount * 9
            } else if (itemStack.persistentDataContainer.has(Shard.namespacedKey)) {
                itemStack.amount
            } else {
                return
            }

        itemStack.amount = 0
        item.itemStack = itemStack

        DiamondBankOG.scope.launch {
            DiamondBankOG.transactionLock.withLockSuspend(player.uniqueId) {
                val error = DiamondBankOG.postgreSQL.addToPlayerShards(player.uniqueId, shards, ShardType.BANK)
                if (error) {
                    handleError(player.uniqueId, shards, null)
                    player.sendMessage(
                        DiamondBankOG.mm.deserialize(
                            "${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                        )
                    )
                    return@withLockSuspend
                }
            }
        }
    }
}
