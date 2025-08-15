package net.trueog.diamondbankog.commands

import java.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.PlayerPrefix.getPrefix
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SetBankBalance : CommandExecutor {
    @OptIn(DelicateCoroutinesApi::class)
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

            if (!sender.hasPermission("diamondbank-og.setbankbalance")) {
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
                        "${Config.prefix}<reset>: <red>You did not provide the name or the UUID of a player and the amount of <aqua>Shards<red>."
                    )
                )
                return@launch
            }
            if (args.size != 2) {
                sender.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>Incorrect syntax. Usage: /setbankbal(ance) <player name or player uuid> <shards>."
                    )
                )
                return@launch
            }

            val player =
                try {
                    Bukkit.getPlayer(UUID.fromString(args[0])) ?: Bukkit.getOfflinePlayer(UUID.fromString(args[0]))
                } catch (_: Exception) {
                    Bukkit.getPlayer(args[0]) ?: Bukkit.getOfflinePlayer(args[0])
                }
            if (!player.hasPlayedBefore()) {
                sender.sendMessage(
                    DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>That player doesn't exist.")
                )
                return@launch
            }

            val balance: Int
            try {
                balance = args[1].toInt()
            } catch (_: Exception) {
                sender.sendMessage(DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <red>Invalid argument."))
                return@launch
            }

            var error = DiamondBankOG.postgreSQL.setPlayerShards(player.uniqueId, balance, ShardType.BANK)
            if (error) {
                sender.sendMessage(
                    DiamondBankOG.mm.deserialize(
                        "${Config.prefix}<reset>: <red>Something went wrong while trying to set that player's balance."
                    )
                )
                return@launch
            }
            sender.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${Config.prefix}<reset>: <green>Successfully set the balance of ${
                        getPrefix(
                            player.uniqueId
                        )
                    }${player.name}<reset> <green>to <yellow>$balance <aqua>${if (balance == 1) "Shard" else "Shards"}<green>."
                )
            )

            error =
                DiamondBankOG.postgreSQL.insertTransactionLog(
                    player.uniqueId,
                    balance,
                    null,
                    "Set Bank Balance",
                    "Bank Balance set by ${if (sender is Player) "${sender.uniqueId}" else "console"}",
                )
            if (error) {
                handleError(player.uniqueId, balance, null, null, true)
            }
        }
        return true
    }
}
