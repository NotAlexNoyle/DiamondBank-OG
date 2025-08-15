package net.trueog.diamondbankog

import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.general.ArrayRowData
import com.github.jasync.sql.db.pool.ConnectionPool
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import java.sql.SQLException
import java.util.*
import kotlinx.coroutines.future.await

class PostgreSQL {
    lateinit var pool: ConnectionPool<PostgreSQLConnection>

    enum class ShardType(val string: String) {
        BANK("bank_shards"),
        INVENTORY("inventory_shards"),
        ENDER_CHEST("ender_chest_shards"),
        ALL("bank_shards, inventory_shards, ender_chest_shards"),
    }

    @Throws(SQLException::class, ClassNotFoundException::class)
    fun initDB() {
        try {
            pool =
                PostgreSQLConnectionBuilder.createConnectionPool(
                    "${Config.postgresUrl}?user=${Config.postgresUser}&password=${Config.postgresPassword}"
                )
            val createTable =
                pool.sendPreparedStatement(
                    "CREATE TABLE IF NOT EXISTS ${Config.postgresTable}(uuid UUID PRIMARY KEY, bank_shards INTEGER, inventory_shards INTEGER, ender_chest_shards INTEGER, total_shards INTEGER GENERATED ALWAYS AS ( COALESCE(bank_shards, 0) + COALESCE(inventory_shards, 0) + COALESCE(ender_chest_shards, 0) ) STORED)"
                )
            createTable.join()
            val createTotalShardsIndex =
                pool.sendPreparedStatement(
                    "CREATE INDEX IF NOT EXISTS idx_total_shards ON ${Config.postgresTable}(total_shards DESC)"
                )
            createTotalShardsIndex.join()

            val createLogTable =
                pool.sendPreparedStatement(
                    "CREATE TABLE IF NOT EXISTS ${Config.postgresLogTable}(id SERIAL PRIMARY KEY, player_uuid UUID NOT NULL, transferred_shards INTEGER NOT NULL, player_to_uuid UUID, transaction_reason TEXT, notes TEXT, timestamp TIMESTAMPTZ DEFAULT NOW())"
                )
            createLogTable.join()
            // @formatter:off
            val createFunction =
                pool.sendPreparedStatement(
                    "CREATE OR REPLACE FUNCTION fifo_limit_trigger() RETURNS TRIGGER AS $$" +
                        "DECLARE " +
                        "max_rows CONSTANT INTEGER := ${Config.postgresLogLimit};" +
                        "BEGIN " +
                        "DELETE FROM ${Config.postgresLogTable} " +
                        "WHERE id <= (" +
                        "SELECT id FROM ${Config.postgresLogTable} " +
                        "ORDER BY id DESC " +
                        "OFFSET max_rows LIMIT 1" +
                        ");" +
                        "RETURN NEW;" +
                        "END;" +
                        "$$ LANGUAGE plpgsql;"
                )
            // @formatter:on
            createFunction.join()
            val createTrigger =
                pool.sendPreparedStatement(
                    "CREATE OR REPLACE TRIGGER fifo_limit_trigger\n" +
                        "AFTER INSERT ON ${Config.postgresLogTable}\n" +
                        "FOR EACH STATEMENT\n" +
                        "EXECUTE FUNCTION fifo_limit_trigger();"
                )
            createTrigger.join()
        } catch (_: Exception) {
            DiamondBankOG.economyDisabled = true
            DiamondBankOG.plugin.logger.severe(
                "ECONOMY DISABLED! Something went wrong while trying to initialise PostgreSQL. Is PostgreSQL running? Are the PostgreSQL config variables correct?"
            )
            return
        }
    }

    data class PlayerShards(val shardsInBank: Int?, val shardsInInventory: Int?, val shardsInEnderChest: Int?) {
        fun isNeededShardTypeNull(type: ShardType): Boolean {
            when (type) {
                ShardType.BANK -> {
                    if (this.shardsInBank == null) {
                        return true
                    }
                }

                ShardType.INVENTORY -> {
                    if (this.shardsInInventory == null) {
                        return true
                    }
                }

                ShardType.ENDER_CHEST -> {
                    if (this.shardsInEnderChest == null) {
                        return true
                    }
                }

                ShardType.ALL -> {
                    if (
                        this.shardsInBank == null || this.shardsInInventory == null || this.shardsInEnderChest == null
                    ) {
                        return true
                    }
                }
            }
            return false
        }
    }

    /** @return True if failed */
    suspend fun setPlayerShards(uuid: UUID, shards: Int, type: ShardType): Boolean {
        if (type == ShardType.ALL) return true
        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement(
                    "INSERT INTO ${Config.postgresTable}(uuid, ${type.string}) VALUES(?, ?) ON CONFLICT (uuid) DO UPDATE SET ${type.string} = excluded.${type.string}",
                    listOf(uuid, shards),
                )
            preparedStatement.await()
        } catch (_: Exception) {
            return true
        }
        return false
    }

    /** @return True if failed */
    suspend fun addToPlayerShards(uuid: UUID, shards: Int, type: ShardType): Boolean {
        if (type == ShardType.ALL) return true

        val playerDiamonds = getPlayerShardsWrapper(uuid, type) ?: return true

        val error = setPlayerShards(uuid, playerDiamonds + shards, type)
        return error
    }

    /** @return True if failed */
    suspend fun subtractFromPlayerShards(uuid: UUID, shards: Int, type: ShardType): Boolean {
        if (type == ShardType.ALL) return true

        val playerDiamonds = getPlayerShardsWrapper(uuid, type) ?: return true

        val error = setPlayerShards(uuid, playerDiamonds - shards, type)
        return error
    }

    private suspend fun getPlayerShardsWrapper(uuid: UUID, type: ShardType): Int? {
        val getResponse = getPlayerShards(uuid, type)

        return when (type) {
            ShardType.BANK -> getResponse.shardsInBank
            ShardType.INVENTORY -> getResponse.shardsInInventory
            ShardType.ENDER_CHEST -> getResponse.shardsInEnderChest
            ShardType.ALL ->
                if (
                    getResponse.shardsInBank != null &&
                        getResponse.shardsInInventory != null &&
                        getResponse.shardsInEnderChest != null
                )
                    getResponse.shardsInBank + getResponse.shardsInInventory + getResponse.shardsInEnderChest
                else null
        }
    }

    suspend fun getPlayerShards(uuid: UUID, type: ShardType): PlayerShards {
        var bankShards: Int? = null
        var inventoryShards: Int? = null
        var enderChestShards: Int? = null
        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement(
                    "SELECT ${type.string} FROM ${Config.postgresTable} WHERE uuid = ? LIMIT 1",
                    listOf(uuid),
                )
            val result = preparedStatement.await()

            if (result.rows.isNotEmpty()) {
                val rowData = result.rows[0] as ArrayRowData

                when (type) {
                    ShardType.BANK -> {
                        bankShards =
                            if (rowData.columns[0] != null) {
                                rowData.columns[0] as Int
                            } else 0
                    }

                    ShardType.INVENTORY -> {
                        inventoryShards =
                            if (rowData.columns[0] != null) {
                                rowData.columns[0] as Int
                            } else 0
                    }

                    ShardType.ENDER_CHEST -> {
                        enderChestShards =
                            if (rowData.columns[0] != null) {
                                rowData.columns[0] as Int
                            } else 0
                    }

                    ShardType.ALL -> {
                        bankShards =
                            if (rowData.columns[0] != null) {
                                rowData.columns[0] as Int
                            } else 0
                        inventoryShards =
                            if (rowData.columns[1] != null) {
                                rowData.columns[1] as Int
                            } else 0
                        enderChestShards =
                            if (rowData.columns[2] != null) {
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
            DiamondBankOG.plugin.logger.severe(e.toString())
        }

        return PlayerShards(bankShards, inventoryShards, enderChestShards)
    }

    suspend fun getBaltop(offset: Int): Map<UUID?, Int>? {
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement(
                    "SELECT uuid, total_shards " +
                        "FROM ${Config.postgresTable} " +
                        "ORDER BY total_shards DESC, uuid DESC OFFSET ? LIMIT 9",
                    listOf(offset),
                )
            val result = preparedStatement.await()
            val baltop = mutableMapOf<UUID?, Int>()
            result.rows.forEach {
                val rowData = it as ArrayRowData
                val uuid =
                    if (rowData.columns[0] != null) {
                        rowData.columns[0] as UUID
                    } else null
                val totalShards =
                    if (rowData.columns[1] != null) {
                        rowData.columns[1] as Int
                    } else 0

                baltop[uuid] = totalShards
            }
            return baltop
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.severe(e.toString())
        }
        return null
    }

    /**
     * @return Pair with as the first value a map with the player name and total balance and as the second value the
     *   offset
     */
    suspend fun getBaltopWithUuid(uuid: UUID): Pair<Map<UUID?, Int>, Long>? {
        try {
            val connection = pool.asSuspending.connect()
            // @formatter:off
            val preparedStatement =
                connection.sendPreparedStatement(
                    "WITH ranked AS (" +
                        "SELECT " +
                        "uuid, total_shards, ROW_NUMBER() OVER (ORDER BY total_shards DESC, uuid DESC) AS rn " +
                        "FROM ${Config.postgresTable}" +
                        "), " +
                        "target AS (" +
                        "SELECT rn, ((rn - 1) / 9) * 9 AS page_offset FROM ranked WHERE uuid = ?), " +
                        "paged AS (" +
                        "SELECT ranked.*, target.page_offset FROM ranked JOIN target ON true " +
                        "WHERE ranked.rn > target.page_offset AND ranked.rn <= target.page_offset + 9" +
                        ") " +
                        "SELECT uuid, total_shards, page_offset FROM paged ORDER BY rn",
                    listOf(uuid),
                )
            // @formatter:on
            val result = preparedStatement.await()
            val baltop = mutableMapOf<UUID?, Int>()
            var offset = 0L
            result.rows.forEach {
                val rowData = it as ArrayRowData
                val uuid =
                    if (rowData.columns[0] != null) {
                        rowData.columns[0] as UUID
                    } else null
                val totalShards =
                    if (rowData.columns[1] != null) {
                        rowData.columns[1] as Int
                    } else 0

                offset =
                    if (rowData.columns[2] != null) {
                        rowData.columns[2] as Long
                    } else 0L

                baltop[uuid] = totalShards
            }
            return Pair(baltop, offset)
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.severe(e.toString())
        }
        return null
    }

    suspend fun getNumberOfRows(): Long? {
        var number: Long? = null
        try {
            val connection = pool.asSuspending.connect()
            val preparedStatement =
                connection.sendPreparedStatement("SELECT count(*) AS exact_count FROM ${Config.postgresTable}")
            val result = preparedStatement.await()

            if (result.rows.isNotEmpty()) {
                val rowData = result.rows[0] as ArrayRowData
                number =
                    if (rowData.columns[0] != null) {
                        rowData.columns[0] as Long
                    } else 0
            }
        } catch (e: Exception) {
            DiamondBankOG.plugin.logger.severe(e.toString())
        }
        return number
    }

    /** @return True if failed */
    suspend fun insertTransactionLog(
        playerUuid: UUID,
        transferredShards: Int,
        playerToUuid: UUID?,
        transactionReason: String,
        notes: String?,
    ): Boolean {
        try {
            val connection = pool.asSuspending.connect()

            val preparedStatement =
                connection.sendPreparedStatement(
                    "INSERT INTO ${Config.postgresLogTable}(player_uuid, transferred_shards, player_to_uuid, transaction_reason, notes) " +
                        "VALUES(?, ?, ?, ?, ?)",
                    listOf(playerUuid, transferredShards, playerToUuid, transactionReason, notes),
                )
            preparedStatement.await()
        } catch (_: Exception) {
            return true
        }
        return false
    }
}
