package net.trueog.diamondbankog

import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.scheduler.BukkitRunnable

object InventoryExtensions {
    private suspend fun Inventory.withdrawShards(shards: Int): Int {
        val removeMap = runOnMainThread { this.removeItem(Shard.createItemStack(shards)) }

        if (removeMap.isNotEmpty()) {
            return removeMap[0]!!.amount
        }
        return 0
    }

    private suspend fun Inventory.withdrawDiamonds(shards: Int, player: Player? = null): Int {
        val diamondsNeeded = ceil(shards / 9.0).toInt()
        val remainder = shards % 9
        val change = if (remainder == 0) 0 else 9 - remainder

        val removeMap = runOnMainThread { this.removeItem(ItemStack(Material.DIAMOND, diamondsNeeded)) }

        if (removeMap.isEmpty()) {
            if (change != 0) {
                val error =
                    if (player != null) {
                        this.addBackShards(change, player)
                    } else {
                        this.addBackShards(change)
                    }
                if (error) return -1
            }
            return 0
        }
        return removeMap[0]!!.amount * 9 - change
    }

    private suspend fun Inventory.withdrawDiamondBlocks(shards: Int, player: Player? = null): Int {
        val blocksNeeded = ceil(shards / 81.0).toInt()
        val remainder = shards % 81
        val change = if (remainder == 0) 0 else 81 - remainder
        val diamondChange = floor(change / 9.0).toInt()
        val shardChange = change % 9

        val removeMap = runOnMainThread { this.removeItem(ItemStack(Material.DIAMOND_BLOCK, blocksNeeded)) }

        if (removeMap.isEmpty()) {
            if (change != 0) {
                val leftOver =
                    if (player != null) {
                        this.addBackDiamonds(diamondChange, player)
                    } else {
                        this.addBackDiamonds(diamondChange)
                    }
                val error = this.addBackShards(shardChange + leftOver)
                if (error) return -1
            }
            return 0
        }

        return removeMap[0]!!.amount * 9 * 9 - change
    }

    private suspend fun Inventory.addBackShards(shards: Int): Boolean {
        val player = this.holder as Player

        val addMap = runOnMainThread { this.addItem(Shard.createItemStack(shards)) }

        if (addMap.isEmpty()) return false

        val leftOver = addMap[0]!!.amount

        val error = DiamondBankOG.postgreSQL.addToPlayerShards(player.uniqueId, leftOver, ShardType.BANK)
        player.sendMessage(
            DiamondBankOG.mm.deserialize(
                "${Config.prefix}<reset>: The change of $leftOver <aqua>Diamond ${if (leftOver == 1) "Shard" else "Shards"} <reset>has been deposited into your bank."
            )
        )
        return error
    }

    /** ONLY USE FOR INVENTORIES THAT DO NOT HAVE A HOLDER (for example shulker boxes) */
    private suspend fun Inventory.addBackShards(shards: Int, player: Player): Boolean {
        val result = runOnMainThread {
            val addMap = this.addItem(Shard.createItemStack(shards))
            if (addMap.isEmpty()) return@runOnMainThread null

            val leftOver = addMap[0]!!.amount

            Pair(player.inventory.addItem(Shard.createItemStack(leftOver)), leftOver)
        }
        if (result == null) return false
        val (inventoryAddMap, leftOver) = result

        if (inventoryAddMap.isNotEmpty()) {
            val inventoryLeftOver = inventoryAddMap[0]!!.amount
            val error = DiamondBankOG.postgreSQL.addToPlayerShards(player.uniqueId, inventoryLeftOver, ShardType.BANK)
            if (error) return true
            player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${Config.prefix}<reset>: The change of ${leftOver - inventoryLeftOver} <aqua>Diamond ${if (leftOver == 1) "Shard" else "Shards"} <reset>has been added to your inventory, and the remaining $inventoryLeftOver <aqua>Diamond ${if (leftOver == 1) "Shard" else "Shards"} <reset> has been deposited into your bank."
                )
            )
            return false
        }
        player.sendMessage(
            DiamondBankOG.mm.deserialize(
                "${Config.prefix}<reset>: The change of $leftOver <aqua>Diamond ${if (leftOver == 1) "Shard" else "Shards"} <reset>has been added to your inventory."
            )
        )
        return false
    }

    private suspend fun Inventory.addBackDiamonds(diamonds: Int): Int {
        val addMap = runOnMainThread { this.addItem(ItemStack(Material.DIAMOND, diamonds)) }
        if (addMap.isEmpty()) return 0

        val leftOver = addMap[0]!!.amount

        return leftOver * 9
    }

    /** ONLY USE FOR INVENTORIES THAT DO NOT HAVE A HOLDER (for example shulker boxes) */
    private suspend fun Inventory.addBackDiamonds(diamonds: Int, player: Player): Int {
        val result = runOnMainThread {
            val addMap = this.addItem(ItemStack(Material.DIAMOND, diamonds))
            if (addMap.isEmpty()) return@runOnMainThread null

            val leftOver = addMap[0]!!.amount

            Pair(player.inventory.addItem(ItemStack(Material.DIAMOND, leftOver)), leftOver)
        }
        if (result == null) return 0
        val (inventoryAddMap, leftOver) = result

        if (inventoryAddMap.isNotEmpty()) {
            return inventoryAddMap[0]!!.amount * 9
        }

        player.sendMessage(
            DiamondBankOG.mm.deserialize(
                "${Config.prefix}<reset>: The change of $leftOver <aqua>Diamond${if (leftOver != 1) "s" else ""} <reset>has been added to your inventory."
            )
        )
        return 0
    }

    /** @return The amount of not removed shards, -1 if error */
    suspend fun Inventory.withdraw(shards: Int): Int {
        if (this.holder !is Player) return -1
        val player = this.holder as Player

        var notRemoved = shards

        notRemoved = this.withdrawShards(notRemoved)
        if (notRemoved != 0) {
            notRemoved = this.withdrawDiamonds(notRemoved)
            if (notRemoved != 0) {
                notRemoved = this.withdrawDiamondBlocks(notRemoved)
                if (notRemoved != 0) {
                    val shulkerBoxes = this.all(Material.SHULKER_BOX).values

                    notRemoved =
                        if (shulkerBoxes.isNotEmpty()) {
                            this.all(Material.SHULKER_BOX).values.fold(notRemoved) { notRemovedShulkersGlobal, it ->
                                val blockStateMeta = it.itemMeta as BlockStateMeta
                                val blockState = blockStateMeta.blockState as ShulkerBox
                                val notRemoved = blockState.inventory.shulkerWithdraw(notRemovedShulkersGlobal, player)
                                blockStateMeta.blockState = blockState
                                it.itemMeta = blockStateMeta
                                notRemoved
                            }
                        } else {
                            notRemoved
                        }
                }
            }
        }

        if (this.type == InventoryType.PLAYER) {
            object : BukkitRunnable() {
                    override fun run() {
                        DiamondBankOG.scope.launch {
                            DiamondBankOG.transactionLock.withLockSuspend(player.uniqueId) {
                                val inventoryShards = player.inventory.countTotal()
                                val error =
                                    DiamondBankOG.postgreSQL.setPlayerShards(
                                        player.uniqueId,
                                        inventoryShards,
                                        ShardType.INVENTORY,
                                    )
                                if (error) {
                                    handleError(player.uniqueId, inventoryShards, null)
                                    player.sendMessage(
                                        DiamondBankOG.mm.deserialize(
                                            "${Config.prefix}<reset>: <red>Something went wrong while trying to recount the <aqua>Diamonds<red> amount in your inventory, try opening and closing your inventory to force a recount."
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                .runTaskLater(DiamondBankOG.plugin, 1L)
        }

        return notRemoved
    }

    /** @return The amount of not removed shards */
    suspend fun Inventory.shulkerWithdraw(shards: Int, player: Player): Int {
        val shardsNotRemoved = this.withdrawShards(shards)
        if (shardsNotRemoved != 0) {
            val shardsNotRemovedDiamonds = this.withdrawDiamonds(shardsNotRemoved, player)
            if (shardsNotRemovedDiamonds != 0) {
                val shardsNotRemovedBlocks = this.withdrawDiamondBlocks(shardsNotRemovedDiamonds, player)
                if (shardsNotRemovedBlocks != 0) {
                    return shardsNotRemovedBlocks
                }
            }
        }

        return 0
    }

    fun Inventory.countTotal(): Int {
        return this.countShards() +
            this.countDiamonds() * 9 +
            this.countDiamondBlocks() * 81 +
            this.all(Material.SHULKER_BOX).values.sumOf {
                ((it.itemMeta as BlockStateMeta).blockState as ShulkerBox).inventory.countTotal()
            }
    }

    fun Inventory.countShards(): Int {
        val inventoryShards =
            this.all(Material.PRISMARINE_SHARD)
                .values
                .filter { it.persistentDataContainer.has(Shard.namespacedKey) }
                .sumOf { it.amount }
        return inventoryShards
    }

    fun Inventory.countDiamonds(): Int {
        val inventoryDiamonds = this.all(Material.DIAMOND).values.sumOf { it.amount }
        return inventoryDiamonds
    }

    fun Inventory.countDiamondBlocks(): Int {
        val inventoryDiamondBlocks = this.all(Material.DIAMOND_BLOCK).values.sumOf { it.amount }
        return inventoryDiamondBlocks
    }
}
