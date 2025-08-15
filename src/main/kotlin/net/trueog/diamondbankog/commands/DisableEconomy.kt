package net.trueog.diamondbankog.commands

import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class DisableEconomy : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (!sender.hasPermission("diamondbank-og.admin")) {
            sender.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${Config.prefix}<reset>: <red>You do not have permission to use this command."
                )
            )
            return true
        }

        DiamondBankOG.economyDisabled = true
        sender.sendMessage(
            DiamondBankOG.mm.deserialize(
                "${Config.prefix}<reset>: <green>Successfully <red>disabled <green>the economy."
            )
        )
        return true
    }
}
