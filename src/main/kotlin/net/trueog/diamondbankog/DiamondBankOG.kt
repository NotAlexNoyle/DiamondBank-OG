package net.trueog.diamondbankog

import kotlinx.coroutines.*
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.luckperms.api.LuckPerms
import net.trueog.diamondbankog.commands.*
import net.trueog.diamondbankog.commands.AutoCompress
import net.trueog.diamondbankog.commands.AutoDeposit
import org.bukkit.Bukkit
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin

class DiamondBankOG : JavaPlugin() {
    companion object {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        lateinit var plugin: DiamondBankOG
        lateinit var postgreSQL: PostgreSQL
        lateinit var redis: Redis
        lateinit var luckPerms: LuckPerms

        fun isPostgreSQLInitialised() = ::postgreSQL.isInitialized

        var mm =
            MiniMessage.builder()
                .tags(
                    TagResolver.builder()
                        .resolver(StandardTags.color())
                        .resolver(StandardTags.decorations())
                        .resolver(StandardTags.rainbow())
                        .resolver(StandardTags.reset())
                        .build()
                )
                .build()

        val transactionLock = TransactionLock()
        var economyDisabled: Boolean = false
    }

    override fun onEnable() {
        plugin = this

        if (Config.load()) {
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }

        postgreSQL = PostgreSQL()
        try {
            postgreSQL.initDB()
        } catch (e: Exception) {
            plugin.logger.info(e.toString())
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }

        redis = Redis()
        if (redis.testConnection()) {
            logger.severe("Could not connect to Redis")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }

        val luckPermsProvider = Bukkit.getServicesManager().getRegistration(LuckPerms::class.java)
        if (luckPermsProvider == null) {
            this.logger.severe("Luckperms API is null, quitting...")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }
        luckPerms = luckPermsProvider.provider

        this.server.pluginManager.registerEvents(Events(), this)

        this.getCommand("deposit")?.setExecutor(Deposit())
        this.getCommand("withdraw")?.setExecutor(Withdraw())
        this.getCommand("setbankbalance")?.setExecutor(SetBankBalance())
        this.getCommand("setbankbal")?.setExecutor(SetBankBalance())
        this.getCommand("pay")?.setExecutor(Pay())
        this.getCommand("balancetop")?.setExecutor(Balancetop())
        this.getCommand("baltop")?.setExecutor(Balancetop())
        this.getCommand("balance")?.setExecutor(Balance())
        this.getCommand("bal")?.setExecutor(Balance())
        this.getCommand("compress")?.setExecutor(Compress())
        this.getCommand("autocompress")?.setExecutor(AutoCompress())
        this.getCommand("autodeposit")?.setExecutor(AutoDeposit())

        this.getCommand("diamondbankreload")?.setExecutor(DiamondBankReload())
        this.getCommand("diamondbankhelp")?.setExecutor(DiamondBankHelp())

        this.getCommand("enableeconomy")?.setExecutor(EnableEconomy())
        this.getCommand("disableeconomy")?.setExecutor(DisableEconomy())

        Shard.createCraftingRecipes()

        val diamondBankAPI = DiamondBankAPI(postgreSQL)
        this.server.servicesManager.register(DiamondBankAPI::class.java, diamondBankAPI, this, ServicePriority.Normal)
    }

    override fun onDisable() {
        scope.cancel()

        runBlocking { scope.coroutineContext[Job]?.join() }

        transactionLock.removeAllLocks()

        redis.shutdown()

        if (isPostgreSQLInitialised()) postgreSQL.pool.disconnect().get()
    }
}
