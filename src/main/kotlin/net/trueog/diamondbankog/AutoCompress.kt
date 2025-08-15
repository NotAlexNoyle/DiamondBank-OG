package net.trueog.diamondbankog

import kotlin.math.abs
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.InventoryExtensions.countDiamondBlocks
import net.trueog.diamondbankog.InventoryExtensions.countDiamonds
import net.trueog.diamondbankog.InventoryExtensions.countShards
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object AutoCompress {
    fun compress(player: Player) {
        DiamondBankOG.scope.launch {
            val worldName = player.world.name
            if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") {
                return@launch
            }

            if (!player.hasPermission("diamondbank-og.compress")) {
                player.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>You do not have permission to compress."
                    )
                )
                return@launch
            }

            DiamondBankOG.transactionLock.withLockSuspend(player.uniqueId) {
                runOnMainThread {
                    val shardsInInventory = player.inventory.countShards()
                    val diamondsInInventory = player.inventory.countDiamonds()
                    val diamondBlocksInInventory = player.inventory.countDiamondBlocks()

                    var finalShards = shardsInInventory
                    var finalDiamonds = diamondsInInventory
                    var finalDiamondBlocks = diamondBlocksInInventory

                    finalDiamonds += finalShards / 9
                    if (finalDiamonds - diamondsInInventory != 0) {
                        val remainder = finalShards % 9
                        finalShards = remainder
                    }

                    finalDiamondBlocks += finalDiamonds / 9
                    if (finalDiamondBlocks - diamondBlocksInInventory != 0) {
                        val remainder = finalDiamonds % 9
                        finalDiamonds = remainder
                    }

                    val changeInShards = finalShards - shardsInInventory
                    val changeInDiamonds = finalDiamonds - diamondsInInventory
                    val changeInDiamondBlocks = finalDiamondBlocks - diamondBlocksInInventory

                    if (changeInDiamonds > 0) {
                        val emptySlots = player.inventory.storageContents.filter { it == null }.size * 64
                        val leftOverSpaceDiamonds =
                            player.inventory.storageContents
                                .filterNotNull()
                                .filter { it.type == Material.DIAMOND }
                                .sumOf { 64 - it.amount }

                        if (changeInDiamonds > emptySlots + leftOverSpaceDiamonds) {
                            player.sendMessage(
                                DiamondBankOG.mm.deserialize(
                                    "${Config.prefix}<reset>: <red>You do not have enough space in your inventory to compress all the Diamond-related items (<green>+$changeInDiamonds <aqua>Diamonds<red>)."
                                )
                            )
                            return@runOnMainThread
                        }
                    }

                    if (changeInDiamondBlocks > 0) {
                        val emptySlots = player.inventory.storageContents.filter { it == null }.size * 64
                        val leftOverSpaceDiamondBlocks =
                            player.inventory.storageContents
                                .filterNotNull()
                                .filter { it.type == Material.DIAMOND_BLOCK }
                                .sumOf { 64 - it.amount }

                        if (changeInDiamondBlocks > emptySlots + leftOverSpaceDiamondBlocks) {
                            player.sendMessage(
                                DiamondBankOG.mm.deserialize(
                                    "${Config.prefix}<reset>: <red>You do not have enough space in your inventory to compress all the Diamond-related items (<green>+$changeInDiamondBlocks <aqua>Diamond Blocks<red>)."
                                )
                            )
                            return@runOnMainThread
                        }
                    }

                    if (changeInShards < 0) {
                        val removeMap = player.inventory.removeItem(Shard.createItemStack(abs(changeInShards)))
                        if (removeMap.isNotEmpty()) {
                            player.sendMessage(
                                DiamondBankOG.mm.deserialize(
                                    "${Config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond-related items in your inventory."
                                )
                            )
                            return@runOnMainThread
                        }
                    }

                    if (changeInDiamonds > 0) {
                        val addMap = player.inventory.addItem(ItemStack(Material.DIAMOND, changeInDiamonds))
                        if (addMap.isNotEmpty()) {
                            player.sendMessage(
                                DiamondBankOG.mm.deserialize(
                                    "${Config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond-related items in your inventory."
                                )
                            )
                            return@runOnMainThread
                        }
                    } else if (changeInDiamonds < 0) {
                        val removeMap = player.inventory.removeItem(ItemStack(Material.DIAMOND, abs(changeInDiamonds)))
                        if (removeMap.isNotEmpty()) {
                            player.sendMessage(
                                DiamondBankOG.mm.deserialize(
                                    "${Config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond-related items in your inventory."
                                )
                            )
                            return@runOnMainThread
                        }
                    }

                    if (changeInDiamondBlocks > 0) {
                        val addMap = player.inventory.addItem(ItemStack(Material.DIAMOND_BLOCK, changeInDiamondBlocks))
                        if (addMap.isNotEmpty()) {
                            player.sendMessage(
                                DiamondBankOG.mm.deserialize(
                                    "${Config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond-related items in your inventory."
                                )
                            )
                            return@runOnMainThread
                        }
                    } else if (changeInDiamondBlocks < 0) {
                        val removeMap =
                            player.inventory.removeItem(ItemStack(Material.DIAMOND_BLOCK, abs(changeInDiamondBlocks)))
                        if (removeMap.isNotEmpty()) {
                            player.sendMessage(
                                DiamondBankOG.mm.deserialize(
                                    "${Config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond-related items in your inventory."
                                )
                            )
                            return@runOnMainThread
                        }
                    }
                }
            }
        }
    }
}
