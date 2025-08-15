package net.trueog.diamondbankog

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.AutoCompress.compress
import net.trueog.diamondbankog.AutoDeposit.deposit
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.InventoryExtensions.countTotal
import net.trueog.diamondbankog.MainThreadBlock.runOnMainThread
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable

@OptIn(DelicateCoroutinesApi::class)
class Events : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (DiamondBankOG.economyDisabled) {
            event.player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${Config.prefix}<reset>: <red>The economy is disabled. Please notify a staff member."
                )
            )
            return
        }

        val worldName = event.player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        DiamondBankOG.scope.launch {
            val inventoryShards = event.player.inventory.countTotal()
            var error =
                DiamondBankOG.postgreSQL.setPlayerShards(event.player.uniqueId, inventoryShards, ShardType.INVENTORY)
            if (error) {
                handleError(event.player.uniqueId, inventoryShards, null)
                return@launch
            }

            val enderChestDiamonds = event.player.enderChest.countTotal()
            error =
                DiamondBankOG.postgreSQL.setPlayerShards(
                    event.player.uniqueId,
                    enderChestDiamonds,
                    ShardType.ENDER_CHEST,
                )
            if (error) {
                handleError(event.player.uniqueId, enderChestDiamonds, null)
                return@launch
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity
        if (player !is Player) return
        val worldName = player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        val itemStack = event.item.itemStack
        val itemType = itemStack.type
        if (
            itemType != Material.DIAMOND &&
                itemType != Material.DIAMOND_BLOCK &&
                itemType != Material.SHULKER_BOX &&
                !(itemType == Material.PRISMARINE_SHARD && itemStack.persistentDataContainer.has(Shard.namespacedKey))
        ) {
            return
        }

        if (DiamondBankOG.economyDisabled) {
            player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${Config.prefix}<reset>: <red>You cannot pick up any economy-related items while the economy is disabled."
                )
            )
            event.isCancelled = true
            return
        }

        if (DiamondBankOG.transactionLock.isLocked(player.uniqueId)) {
            event.isCancelled = true
            return
        }

        if (DiamondBankOG.redis.getValue("diamondbankog:${player.uniqueId}:autocompress") == "true") {
            compress(player)
        }

        if (DiamondBankOG.redis.getValue("diamondbankog:${player.uniqueId}:autodeposit") == "true") {
            deposit(player, event.item)
        }

        object : BukkitRunnable() {
                override fun run() {
                    DiamondBankOG.scope.launch {
                        DiamondBankOG.transactionLock.withLockSuspend(player.uniqueId) {
                            val inventoryShards = runOnMainThread { player.inventory.countTotal() }

                            val error =
                                DiamondBankOG.postgreSQL.setPlayerShards(
                                    player.uniqueId,
                                    inventoryShards,
                                    ShardType.INVENTORY,
                                )
                            if (error) {
                                handleError(player.uniqueId, inventoryShards, null)
                                return@withLockSuspend
                            }
                        }
                    }
                }
            }
            .runTaskLater(DiamondBankOG.plugin, 1)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val worldName = event.player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        val itemStack = event.itemDrop.itemStack
        val itemType = itemStack.type
        if (
            itemType != Material.DIAMOND &&
                itemType != Material.DIAMOND_BLOCK &&
                itemType != Material.SHULKER_BOX &&
                !(itemType == Material.PRISMARINE_SHARD && itemStack.persistentDataContainer.has(Shard.namespacedKey))
        ) {
            return
        }

        if (DiamondBankOG.economyDisabled) {
            event.isCancelled = true
            event.player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${Config.prefix}<reset>: <red>You cannot drop any economy-related items while the economy is disabled."
                )
            )
            return
        }

        if (DiamondBankOG.transactionLock.isLocked(event.player.uniqueId)) {
            event.isCancelled = true
            return
        }

        object : BukkitRunnable() {
                override fun run() {
                    DiamondBankOG.scope.launch {
                        DiamondBankOG.transactionLock.withLockSuspend(event.player.uniqueId) {
                            val inventoryShards = runOnMainThread { event.player.inventory.countTotal() }
                            val error =
                                DiamondBankOG.postgreSQL.setPlayerShards(
                                    event.player.uniqueId,
                                    inventoryShards,
                                    ShardType.INVENTORY,
                                )
                            if (error) {
                                handleError(event.player.uniqueId, inventoryShards, null)
                                return@withLockSuspend
                            }
                        }
                    }
                }
            }
            .runTaskLater(DiamondBankOG.plugin, 1)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInventoryMoveItem(event: InventoryClickEvent) {
        val player = event.whoClicked
        if (player !is Player) return
        val worldName = player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        if (event.inventory.type == InventoryType.CRAFTING) return

        val itemStack = event.currentItem
        if (itemStack == null) return
        val itemType = itemStack.type

        if (
            itemType != Material.DIAMOND &&
                itemType != Material.DIAMOND_BLOCK &&
                itemType != Material.SHULKER_BOX &&
                !(itemType == Material.PRISMARINE_SHARD && itemStack.persistentDataContainer.has(Shard.namespacedKey))
        ) {
            return
        }

        if (DiamondBankOG.economyDisabled) {
            event.isCancelled = true
            player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${Config.prefix}<reset>: <red>You cannot move any economy-related items while the economy is disabled."
                )
            )
            return
        }

        if (DiamondBankOG.transactionLock.isLocked(player.uniqueId)) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val worldName = event.player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        if (event.block.type != Material.DIAMOND_BLOCK && event.block.type != Material.SHULKER_BOX) return

        if (DiamondBankOG.economyDisabled) {
            event.isCancelled = true
            event.player.sendMessage(
                DiamondBankOG.mm.deserialize(
                    "${Config.prefix}<reset>: <red>You cannot place any economy-related items while the economy is disabled."
                )
            )
            return
        }

        if (DiamondBankOG.transactionLock.isLocked(event.player.uniqueId)) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (DiamondBankOG.economyDisabled) {
            return
        }

        val player = event.player
        if (player !is Player) return

        if (DiamondBankOG.transactionLock.isLocked(player.uniqueId)) {
            return
        }

        val worldName = player.world.name
        if (worldName != "world" && worldName != "world_nether" && worldName != "world_the_end") return

        if (DiamondBankOG.redis.getValue("diamondbankog:${player.uniqueId}:autocompress") == "true") {
            compress(player)
        }

        object : BukkitRunnable() {
                override fun run() {
                    DiamondBankOG.scope.launch {
                        DiamondBankOG.transactionLock.withLockSuspend(player.uniqueId) {
                            val (inventoryShards, enderChestShards) =
                                runOnMainThread {
                                    Pair(
                                        player.inventory.countTotal(),
                                        if (event.inventory.type == InventoryType.ENDER_CHEST)
                                            player.enderChest.countTotal()
                                        else null,
                                    )
                                }
                            var error =
                                DiamondBankOG.postgreSQL.setPlayerShards(
                                    player.uniqueId,
                                    inventoryShards,
                                    ShardType.INVENTORY,
                                )
                            if (error) {
                                handleError(player.uniqueId, inventoryShards, null)
                                return@withLockSuspend
                            }

                            if (enderChestShards == null) return@withLockSuspend
                            error =
                                DiamondBankOG.postgreSQL.setPlayerShards(
                                    player.uniqueId,
                                    enderChestShards,
                                    ShardType.ENDER_CHEST,
                                )
                            if (error) {
                                handleError(player.uniqueId, enderChestShards, null)
                                return@withLockSuspend
                            }
                        }
                    }
                }
            }
            .runTaskLater(DiamondBankOG.plugin, 1)
    }

    @EventHandler
    fun onPrepareItemCraft(event: PrepareItemCraftEvent) {
        val resultType = event.recipe?.result?.type
        if (
            resultType != Material.PRISMARINE &&
                resultType != Material.DARK_PRISMARINE &&
                resultType != Material.SEA_LANTERN
        ) {
            return
        }
        if (!event.inventory.any { it?.persistentDataContainer?.has(Shard.namespacedKey) == true }) {
            return
        }
        event.inventory.result = ItemStack(Material.AIR)
    }
}
