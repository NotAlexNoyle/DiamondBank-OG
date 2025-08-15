package net.trueog.diamondbankog.commands

import net.trueog.diamondbankog.Config
import net.trueog.diamondbankog.DiamondBankOG
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class DiamondBankHelp : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        sender.sendMessage(
            DiamondBankOG.mm.deserialize(
                "<gray>---- ${Config.prefix}<reset> Help <gray>----<reset>\n" +
                    "<bold>How does the <aqua>Diamonds<white> currency work?<reset>\n" +
                    "The currency on TrueOG Network is based on Diamonds. The smallest form is Diamond Shards, followed by Diamonds, and the largest form is Diamond Blocks. 1 Diamond Block equals 9 Diamonds, and 1 Diamond equals 9 Diamond Shards. When converting Diamond Shards to Diamonds, any amount ending with .9 is rounded up to the next whole Diamond. Some examples:\n" +
                    "- 1.8 Diamonds = 1 Diamond + 8 Shards\n" +
                    "- 1.9 Diamonds = 1 Diamonds + 9 Shards (equal to 2 Diamonds)\n" +
                    "- 2.0 Diamonds = 2 Diamonds\n" +
                    "- 2.1 Diamonds = 2 Diamonds + 1 Shard"
            )
        )
        return true
    }
}
