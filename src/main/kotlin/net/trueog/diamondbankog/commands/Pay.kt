package net.trueog.diamondbankog.commands

import java.util.*
import kotlin.math.floor
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.PlayerPrefix.getPrefix
import net.trueog.diamondbankog.PostgreSQL.ShardType
import net.trueog.diamondbankog.TransactionLock
import net.trueog.diamondbankog.WithdrawHelper
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class Pay : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        DiamondBankOG.scope.launch launch@{
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
                        "${Config.prefix}<reset>: <red>You cannot use /pay when in a minigame."
                    )
                )
                return@launch
            }

            if (!sender.hasPermission("diamondbank-og.pay")) {
                sender.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>You do not have permission to use this command."
                    )
                )
                return@launch
            }

            if (args == null || args.isEmpty()) {
                sender.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>You did not provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."
                    )
                )
                return@launch
            }
            if (args.size != 2) {
                sender.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>Please (only) provide the name or the UUID of a player and the amount of <aqua>Diamonds<red>."
                    )
                )
                return@launch
            }

            val receiver =
                try {
                    Bukkit.getPlayer(UUID.fromString(args[0])) ?: Bukkit.getOfflinePlayer(UUID.fromString(args[0]))
                } catch (_: Exception) {
                    Bukkit.getPlayer(args[0]) ?: Bukkit.getOfflinePlayer(args[0])
                }

            if (sender.uniqueId == receiver.uniqueId) {
                sender.sendMessage(
                    DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>You cannot pay yourself.")
                )
                return@launch
            }

            if (!receiver.hasPlayedBefore()) {
                sender.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>That player doesn't exist or hasn't joined this server before."
                    )
                )
                return@launch
            }

            var shards = -1
            if (args[1] != "all") {
                val amount: Float
                try {
                    amount = args[1].toFloat()
                    if (amount <= 0) {
                        sender.sendMessage(
                            DiamondBankOG.mm.deserialize(
                                "${Config.prefix}<reset>: <red>You cannot pay a negative or zero amount."
                            )
                        )
                        return@launch
                    }
                } catch (_: Exception) {
                    sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Invalid argument."))
                    return@launch
                }
                val split = amount.toString().split(".")
                if (split[1].length > 1) {
                    sender.sendMessage(
                        DiamondBankOG.mm.deserialize(
                            "${Config.prefix}<reset>: <red><aqua>Diamonds<red> can only have one decimal digit. Issue /diamondbankhelp for more information."
                        )
                    )
                    return@launch
                }
                shards = (split[0].toInt() * 9) + split[1].toInt()
            }

            val originalShards = shards

            when (
                val result =
                    DiamondBankOG.transactionLock.tryWithLockSuspend(sender.uniqueId) {
                        val notRemoved = WithdrawHelper.withdrawFromPlayer(sender, shards)
                        if (notRemoved != 0) {
                            if (notRemoved <= -1) {
                                handleError(sender.uniqueId, shards, null, receiver.uniqueId)
                                sender.sendMessage(
                                    DiamondBankOG.mm.deserialize(
                                        "${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                                    )
                                )
                                return@tryWithLockSuspend true
                            }
                            val notRemovedDiamonds = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
                            shards -= notRemoved
                            val diamondsContinuing = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)
                            sender.sendMessage(
                                DiamondBankOG.mm.deserialize(
                                    "${Config.prefix}<reset>: <#FFA500>Something went wrong while trying to remove <yellow>$notRemovedDiamonds <aqua>Diamond${if (notRemovedDiamonds != "1.0") "s" else ""}<#FFA500> from your inventory and/or ender chest, proceeding with <yellow>$diamondsContinuing <aqua>Diamond${if (diamondsContinuing != "1.0") "s" else ""}<#FFA500>."
                                )
                            )
                        }

                        val error =
                            DiamondBankOG.postgreSQL.addToPlayerShards(receiver.uniqueId, shards, ShardType.BANK)
                        if (error) {
                            handleError(sender.uniqueId, shards, null, receiver.uniqueId)
                            sender.sendMessage(
                                DiamondBankOG.mm.deserialize(
                                    "${Config.prefix}<reset>: <red>A severe error has occurred. Please notify a staff member."
                                )
                            )
                            return@tryWithLockSuspend true
                        }
                        false
                    }
            ) {
                is TransactionLock.LockResult.Acquired -> {
                    if (result.result) {
                        return@launch
                    }
                }

                TransactionLock.LockResult.Failed -> {
                    sender.sendMessage(
                        DiamondBankOG.mm.deserialize(
                            "${Config.prefix}<reset>: <red>You are currently blocked from using /pay."
                        )
                    )
                    return@launch
                }
            }

            val diamondsPaid = String.format("%.1f", floor((shards / 9.0) * 10) / 10.0)

            sender.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${Config.prefix}<reset>: <green>Successfully paid <yellow>$diamondsPaid <aqua>Diamond${if (diamondsPaid != "1.0") "s" else ""} <green>to ${
                        getPrefix(
                            receiver.uniqueId
                        )
                    } ${receiver.name}<reset><green>."
                )
            )

            if (receiver.isOnline) {
                val receiverPlayer = receiver.player ?: return@launch
                receiverPlayer.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <green>${
                            getPrefix(
                                sender.uniqueId
                            )
                        } ${sender.name}<reset> <green>has paid you <yellow>$diamondsPaid <aqua>Diamond${if (diamondsPaid != "1.0") "s" else ""}<green>."
                    )
                )
            }

            val error =
                DiamondBankOG.postgreSQL.insertTransactionLog(
                    sender.uniqueId,
                    shards,
                    receiver.uniqueId,
                    "Pay",
                    if (shards != originalShards) "Could not withdraw $originalShards shards, continued with $shards"
                    else null,
                )
            if (error) {
                handleError(sender.uniqueId, shards, null, receiver.uniqueId, true)
            }
        }
        return true
    }
}
