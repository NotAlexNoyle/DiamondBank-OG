package net.trueog.diamondbankog

import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.general.ArrayRowData
import com.github.jasync.sql.db.pool.ConnectionPool
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import kotlinx.coroutines.future.await
import org.bukkit.Bukkit
import java.sql.SQLException
import java.util.*

class PostgreSQL {
    lateinit var pool: ConnectionPool<PostgreSQLConnection>

    enum class DiamondType(val string: String) {
        BANK("bank_diamonds"), INVENTORY("inventory_diamonds"), ENDER_CHEST("ender_chest_diamonds"), ALL("bank_diamonds, inventory_diamonds, ender_chest_diamonds")
    }

    enum class ShardType(val string: String) {
        BANK("bank_shards"), INVENTORY("inventory_shards"), ENDER_CHEST("ender_chest_shards"), ALL("bank_shards, inventory_shards, ender_chest_shards")
    }

    @Throws(SQLException::class, ClassNotFoundException::class)
    fun initDB() {
        try {
            pool =
                PostgreSQLConnectionBuilder.createConnectionPool("${Config.postgresUrl}?user=${Config.postgresUser}&password=${Config.postgresPassword}")
            val createTable =
                pool.sendPreparedStatement("CREATE TABLE IF NOT EXISTS ${Config.postgresTable}(uuid TEXT, bank_diamonds integer, inventory_diamonds integer, ender_chest_diamonds integer, bank_shards integer, inventory_shards integer, ender_chest_shards integer, unique(uuid))")
            createTable.join()

            for (column in arrayOf("uuid", "bank_diamonds", "inventory_diamonds", "ender_chest_diamonds", "bank_shards", "inventory_shards", "ender_chest_shards")) {
                val createIndex =
                    pool.sendPreparedStatement("CREATE INDEX IF NOT EXISTS idx_$column ON ${Config.postgresTable}($column)")
                createIndex.join()
            }
        } catch (e: Exception) {
            DiamondBankOG.economyDisabled = true
            DiamondBankOG.plugin.logger.severe("ECONOMY DISABLED! Something went wrong while trying to initialise PostgreSQL. Is PostgreSQL running? Are the PostgreSQL config variables correct?")
            return
        }
    }

    data class GetResponse(val amountInBank: Int?, val amountInInventory: Int?, val amountInEnderChest: Int?)

    suspend fun setPlayerDiamonds(uuid: UUID, diamonds: Int, type: DiamondType): Boolean {
        if (type == DiamondType.ALL) return true

        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement("INSERT INTO ${Config.postgresTable}(uuid, ${type.string}) VALUES('$uuid', $diamonds) ON CONFLICT (uuid) DO UPDATE SET ${type.string} = excluded.${type.string}")
            preparedStatement.await()
        } catch (e: Exception) {
            return true
        }
        return false
    }

    suspend fun addToPlayerDiamonds(uuid: UUID, diamonds: Int, type: DiamondType): Boolean {
        if (type == DiamondType.ALL) return true

        val playerDiamonds = getPlayerDiamondsWrapper(uuid, type) ?: return true

        val error = setPlayerDiamonds(uuid, playerDiamonds + diamonds, type)
        return error
    }

    suspend fun subtractFromPlayerDiamonds(uuid: UUID, diamonds: Int, type: DiamondType): Boolean {
        if (type == DiamondType.ALL) return true

        val getResponse = getPlayerDiamondsWrapper(uuid, type) ?: return true

        val error = setPlayerDiamonds(uuid, getResponse - diamonds, type)
        return error
    }

    private suspend fun getPlayerDiamondsWrapper(uuid: UUID, type: DiamondType): Int? {
        val getResponse = getPlayerDiamonds(uuid, type)

        return when (type) {
            DiamondType.BANK -> getResponse.amountInBank
            DiamondType.INVENTORY -> getResponse.amountInInventory
            DiamondType.ENDER_CHEST -> getResponse.amountInEnderChest
            DiamondType.ALL -> if (getResponse.amountInBank != null && getResponse.amountInInventory != null && getResponse.amountInEnderChest != null) getResponse.amountInBank + getResponse.amountInInventory + getResponse.amountInEnderChest else null
        }
    }

    suspend fun getPlayerDiamonds(uuid: UUID, type: DiamondType): GetResponse {
        var amountInBank: Int? = null
        var amountInInventory: Int? = null
        var amountInEnderChest: Int? = null
        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement("SELECT ${type.string} FROM ${Config.postgresTable} WHERE uuid = '$uuid' LIMIT 1")
            val result = preparedStatement.await()

            if (result.rows.size != 0) {
                val rowData = result.rows[0] as ArrayRowData

                when (type) {
                    DiamondType.BANK -> {
                        amountInBank = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    DiamondType.INVENTORY -> {
                        amountInInventory = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    DiamondType.ENDER_CHEST -> {
                        amountInEnderChest = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    DiamondType.ALL -> {
                        amountInBank = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                        amountInInventory = if (rowData.columns[1] != null) {
                            rowData.columns[1] as Int
                        } else 0
                        amountInEnderChest = if (rowData.columns[2] != null) {
                            rowData.columns[2] as Int
                        } else 0
                    }
                }
            } else {
                amountInBank = 0
                amountInInventory = 0
                amountInEnderChest = 0
            }
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
        }
        return GetResponse(amountInBank, amountInInventory, amountInEnderChest)
    }

    suspend fun setPlayerShards(uuid: UUID, shards: Int, type: ShardType): Boolean {
        if (type == ShardType.ALL) return true
        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement("INSERT INTO ${Config.postgresTable}(uuid, ${type.string}) VALUES('$uuid', $shards) ON CONFLICT (uuid) DO UPDATE SET ${type.string} = excluded.${type.string}")
            preparedStatement.await()
        } catch (e: Exception) {
            return true
        }
        return false
    }

    suspend fun addToPlayerShards(uuid: UUID, shards: Int, type: ShardType): Boolean {
        if (type == ShardType.ALL) return true

        val playerDiamonds = getPlayerShardsWrapper(uuid, type) ?: return true

        val error = setPlayerShards(uuid, playerDiamonds + shards, type)
        return error
    }

    suspend fun subtractFromPlayerShards(uuid: UUID, shards: Int, type: ShardType): Boolean {
        if (type == ShardType.ALL) return true

        val playerDiamonds = getPlayerShardsWrapper(uuid, type) ?: return true

        val error = setPlayerShards(uuid, playerDiamonds - shards, type)
        return error
    }

    private suspend fun getPlayerShardsWrapper(uuid: UUID, type: ShardType): Int? {
        val getResponse = getPlayerShards(uuid, type)

        return when (type) {
            ShardType.BANK -> getResponse.amountInBank
            ShardType.INVENTORY -> getResponse.amountInInventory
            ShardType.ENDER_CHEST -> getResponse.amountInEnderChest
            ShardType.ALL -> if (getResponse.amountInBank != null && getResponse.amountInInventory != null && getResponse.amountInEnderChest != null) getResponse.amountInBank + getResponse.amountInInventory + getResponse.amountInEnderChest else null
        }
    }

    suspend fun getPlayerShards(uuid: UUID, type: ShardType): GetResponse {
        var bankShards: Int? = null
        var inventoryShards: Int? = null
        var enderChestShards: Int? = null
        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement("SELECT ${type.string} FROM ${Config.postgresTable} WHERE uuid = '$uuid' LIMIT 1")
            val result = preparedStatement.await()

            if (result.rows.size != 0) {
                val rowData = result.rows[0] as ArrayRowData

                when (type) {
                    ShardType.BANK -> {
                        bankShards = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    ShardType.INVENTORY -> {
                        inventoryShards = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    ShardType.ENDER_CHEST -> {
                        enderChestShards = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                    }

                    ShardType.ALL -> {
                        bankShards = if (rowData.columns[0] != null) {
                            rowData.columns[0] as Int
                        } else 0
                        inventoryShards = if (rowData.columns[1] != null) {
                            rowData.columns[1] as Int
                        } else 0
                        enderChestShards = if (rowData.columns[2] != null) {
                            rowData.columns[2] as Int
                        } else 0
                    }
                }
            } else {
                bankShards = 0
                inventoryShards = 0
                enderChestShards = 0
            }
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
        }
        return GetResponse(bankShards, inventoryShards, enderChestShards)
    }

    suspend fun getBaltop(offset: Int): MutableMap<String?, Int>? {
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("SELECT * FROM ${Config.postgresTable} ORDER BY bank_diamonds DESC, inventory_diamonds DESC, ender_chest_diamonds DESC OFFSET $offset LIMIT 10")
            val result = preparedStatement.await()
            val baltop = mutableMapOf<String?, Int>()
            result.rows.forEach {
                val rowData = it as ArrayRowData
                val bankDiamonds = if (rowData.columns[1] != null) {
                    rowData.columns[1] as Int
                } else 0
                val inventoryDiamonds = if (rowData.columns[2] != null) {
                    rowData.columns[2] as Int
                } else 0
                val enderChestDiamonds = if (rowData.columns[3] != null) {
                    rowData.columns[3] as Int
                } else 0

                val player = Bukkit.getPlayer(UUID.fromString(rowData.columns[0] as String)) ?: Bukkit.getOfflinePlayer(
                    UUID.fromString(rowData.columns[0] as String)
                )
                baltop[player.name] = bankDiamonds + inventoryDiamonds + enderChestDiamonds
            }
            return baltop
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
        }
        return null
    }

    suspend fun getNumberOfRows(): Int? {
        var number: Int? = null
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("SELECT count(*) AS exact_count FROM ${Config.postgresTable}")
            val result = preparedStatement.await()

            if (result.rows.size != 0) {
                val rowData = result.rows[0] as ArrayRowData
                number = if (rowData.columns[0] != null) {
                    rowData.columns[0] as Int
                } else 0
            }
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.info(e.toString())
        }
        return number
    }
}
