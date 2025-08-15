package net.trueog.diamondbankog.commands

import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import net.trueog.diamondbankog.PostgreSQL
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class DiamondBankReload : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (Config.load()) {
            sender.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "<red>Failed to reload the config. Check the console for more information."
                )
            )
            Bukkit.getPluginManager().disablePlugin(DiamondBankOG.plugin)
            return true
        }

        DiamondBankOG.postgreSQL = PostgreSQL()
        try {
            DiamondBankOG.postgreSQL.initDB()
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
            sender.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "<red>Failed to reload the config. Check the console for more information."
                )
            )
            Bukkit.getPluginManager().disablePlugin(DiamondBankOG.plugin)
            return true
        }

        sender.sendMessage(
            DiamondBankOG.mm.deserialize("${Config.prefix}<reset>: <green>Successfully reloaded the config.")
        )
        return true
    }
}
