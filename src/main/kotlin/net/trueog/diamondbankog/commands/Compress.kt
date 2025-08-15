package net.trueog.diamondbankog.commands

import kotlin.math.abs
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.InventoryExtensions.countDiamondBlocks
import net.trueog.diamondbankog.InventoryExtensions.countDiamonds
import net.trueog.diamondbankog.InventoryExtensions.countShards
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import net.trueog.diamondbankog.Shard
import net.trueog.diamondbankog.TransactionLock
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta

class Compress : CommandExecutor {
    private data class RunOnMainThreadResult(
        val shouldReturn: Boolean,
        val changeInShards: Int?,
        val changeInDiamonds: Int?,
        val changeInDiamondBlocks: Int?,
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        DiamondBankOG.scope.launch {
            if (DiamondBankOG.economyDisabled) {
                sender.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>The economy is disabled. Please notify a staff member."
                    )
                )
                return@launch
            }

            if (sender !is Player) {
                sender.sendMessage("You can only execute this command as a player.")
                return@launch
            }

            val worldName = sender.world.name
            if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") {
                sender.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>You cannot use /compress when in a minigame."
                    )
                )
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.compress")) {
                sender.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>You do not have permission to use this command."
                    )
                )
                return@launch
            }

            var isShulkerBox = false
            val result =
                DiamondBankOG.transactionLock.tryWithLockSuspend(sender.uniqueId) {
                    runOnMainThread {
                        val (inventory, blockStateMeta, blockState) =
                            if (args != null && args.isNotEmpty()) {
                                if (args.size > 1) {
                                    sender.sendMessage(
                                        DiamondBankOG.mm.deserialize(
                                            "${Config.prefix}<reset>: <red>Do not provide more arguments than \"yes\" if you want to compress the items in the shulker box you're holding."
                                        )
                                    )
                                    return@runOnMainThread RunOnMainThreadResult(true, null, null, null)
                                }
                                if (args[0] != "yes") {
                                    sender.sendMessage(
                                        DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Invalid argument.")
                                    )
                                    return@runOnMainThread RunOnMainThreadResult(true, null, null, null)
                                }

                                val itemInMainHand = sender.inventory.itemInMainHand
                                if (itemInMainHand.type != Material.SHULKER_BOX) {
                                    sender.sendMessage(
                                        DiamondBankOG.mm.deserialize(
                                            "${Config.prefix}<reset>: <red>You are not holding a shulker box."
                                        )
                                    )
                                    return@runOnMainThread RunOnMainThreadResult(true, null, null, null)
                                }

                                val blockStateMeta = itemInMainHand.itemMeta as BlockStateMeta
                                val blockState = blockStateMeta.blockState as ShulkerBox
                                isShulkerBox = true
                                Triple(blockState.inventory, blockStateMeta, blockState)
                            } else {
                                Triple(sender.inventory, null, null)
                            }

                        if (sender.inventory.itemInMainHand.type == Material.SHULKER_BOX && !isShulkerBox) {
                            sender.sendMessage(
                                DiamondBankOG.mm.deserialize(
                                    "${Config.prefix}<reset>: <#FFA500>Are you sure you want to compress the Diamond-related items in the shulker box you're holding? If so, run \"/compress yes\""
                                )
                            )
                            return@runOnMainThread RunOnMainThreadResult(true, null, null, null)
                        }

                        val shardsInInventory = inventory.countShards()
                        val diamondsInInventory = inventory.countDiamonds()
                        val diamondBlocksInInventory = inventory.countDiamondBlocks()

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
                            val emptySlots = inventory.storageContents.filter { it == null }.size * 64
                            val leftOverSpaceDiamonds =
                                inventory.storageContents
                                    .filterNotNull()
                                    .filter { it.type == Material.DIAMOND }
                                    .sumOf { 64 - it.amount }

                            if (changeInDiamonds > emptySlots + leftOverSpaceDiamonds) {
                                sender.sendMessage(
                                    DiamondBankOG.mm.deserialize(
                                        "${Config.prefix}<reset>: <red>You do not have enough space in your ${if (isShulkerBox) "shulker box" else "inventory"} to compress all the Diamond-related items (<green>+$changeInDiamonds <aqua>Diamonds<red>)."
                                    )
                                )
                                return@runOnMainThread RunOnMainThreadResult(true, null, null, null)
                            }
                        }

                        if (changeInDiamondBlocks > 0) {
                            val emptySlots = inventory.storageContents.filter { it == null }.size * 64
                            val leftOverSpaceDiamondBlocks =
                                inventory.storageContents
                                    .filterNotNull()
                                    .filter { it.type == Material.DIAMOND_BLOCK }
                                    .sumOf { 64 - it.amount }

                            if (changeInDiamondBlocks > emptySlots + leftOverSpaceDiamondBlocks) {
                                sender.sendMessage(
                                    DiamondBankOG.mm.deserialize(
                                        "${Config.prefix}<reset>: <red>You do not have enough space in your ${if (isShulkerBox) "shulker box" else "inventory"} to compress all the Diamond-related items (<green>+$changeInDiamondBlocks <aqua>Diamond Blocks<red>)."
                                    )
                                )
                                return@runOnMainThread RunOnMainThreadResult(true, null, null, null)
                            }
                        }

                        if (changeInShards < 0) {
                            val removeMap = inventory.removeItem(Shard.createItemStack(abs(changeInShards)))
                            if (removeMap.isNotEmpty()) {
                                sender.sendMessage(
                                    DiamondBankOG.mm.deserialize(
                                        "${Config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond-related items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                    )
                                )
                                return@runOnMainThread RunOnMainThreadResult(true, null, null, null)
                            }
                        }

                        if (changeInDiamonds > 0) {
                            val addMap = inventory.addItem(ItemStack(Material.DIAMOND, changeInDiamonds))
                            if (addMap.isNotEmpty()) {
                                sender.sendMessage(
                                    DiamondBankOG.mm.deserialize(
                                        "${Config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond-related items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                    )
                                )
                                return@runOnMainThread RunOnMainThreadResult(true, null, null, null)
                            }
                        } else if (changeInDiamonds < 0) {
                            val removeMap = inventory.removeItem(ItemStack(Material.DIAMOND, abs(changeInDiamonds)))
                            if (removeMap.isNotEmpty()) {
                                sender.sendMessage(
                                    DiamondBankOG.mm.deserialize(
                                        "${Config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond-related items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                    )
                                )
                                return@runOnMainThread RunOnMainThreadResult(true, null, null, null)
                            }
                        }

                        if (changeInDiamondBlocks > 0) {
                            val addMap = inventory.addItem(ItemStack(Material.DIAMOND_BLOCK, changeInDiamondBlocks))
                            if (addMap.isNotEmpty()) {
                                sender.sendMessage(
                                    DiamondBankOG.mm.deserialize(
                                        "${Config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond-related items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                    )
                                )
                                return@runOnMainThread RunOnMainThreadResult(true, null, null, null)
                            }
                        } else if (changeInDiamondBlocks < 0) {
                            val removeMap =
                                inventory.removeItem(ItemStack(Material.DIAMOND_BLOCK, abs(changeInDiamondBlocks)))
                            if (removeMap.isNotEmpty()) {
                                sender.sendMessage(
                                    DiamondBankOG.mm.deserialize(
                                        "${Config.prefix}<reset>: <red>Something went wrong while trying to compress the Diamond-related items in your ${if (isShulkerBox) "shulker box" else "inventory"}."
                                    )
                                )
                                return@runOnMainThread RunOnMainThreadResult(true, null, null, null)
                            }
                        }

                        if (blockStateMeta != null && blockState != null) {
                            blockStateMeta.blockState = blockState
                            sender.inventory.itemInMainHand.itemMeta = blockStateMeta
                        }

                        RunOnMainThreadResult(false, changeInShards, changeInDiamonds, changeInDiamondBlocks)
                    }
                }
            val (shouldReturn, changeInShards, changeInDiamonds, changeInDiamondBlocks) =
                when (result) {
                    is TransactionLock.LockResult.Acquired -> {
                        result.result
                    }

                    TransactionLock.LockResult.Failed -> {
                        sender.sendMessage(
                            DiamondBankOG.mm.deserialize(
                                "${Config.prefix}<reset>: <red>You are currently blocked from using /deposit."
                            )
                        )
                        RunOnMainThreadResult(true, null, null, null)
                    }
                }

            if (shouldReturn) return@launch
            if (changeInShards == null || changeInDiamonds == null || changeInDiamondBlocks == null) return@launch
            if (changeInShards == 0 && changeInDiamonds == 0 && changeInDiamondBlocks == 0) {
                sender.sendMessage(
                    DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <#FFA500>Nothing found to compress.")
                )
                return@launch
            }

            sender.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${Config.prefix}<reset>: <green>Successfully compressed all the Diamond-related items in your ${if (isShulkerBox) "shulker box" else "inventory"}!"
                )
            )

            val shardsLine =
                if (changeInShards < 0)
                    "|<red>$changeInShards Diamond Shard${if (abs(changeInShards) != 1) "s" else ""}\n"
                else ""
            val diamondsLine =
                (if (changeInDiamonds > 0) "|<green>+$changeInDiamonds"
                else if (changeInDiamonds < 0) "|<red>$changeInDiamonds" else "") +
                    if (changeInDiamonds > 0 || changeInDiamonds < 0)
                        " Diamond${
                    if (abs(
                            changeInDiamonds
                        ) != 1
                    ) "s" else ""
                }\n"
                    else ""
            val diamondBlocksLine =
                (if (changeInDiamondBlocks > 0) "|<green>+$changeInDiamondBlocks"
                else if (changeInDiamondBlocks < 0) "|<red>$changeInDiamondBlocks" else "") +
                    if (changeInDiamondBlocks > 0 || changeInDiamondBlocks < 0)
                        " Diamond Block${
                    if (abs(
                            changeInDiamondBlocks
                        ) != 1
                    ) "s" else ""
                }"
                    else ""

            sender.sendMessage(
                DiamondBankOG.mm.deserialize(
                    buildString {
                            append("${Config.prefix}<reset>: Compression Summary:\n")
                            if (shardsLine.isNotEmpty()) append(shardsLine)
                            if (diamondsLine.isNotEmpty()) append(diamondsLine)
                            if (diamondBlocksLine.isNotEmpty()) append(diamondBlocksLine)
                        }
                        .trimMargin("|")
                )
            )
        }
        return true
    }
}
