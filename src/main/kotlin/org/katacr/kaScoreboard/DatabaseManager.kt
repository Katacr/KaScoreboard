package org.katacr.kaScoreboard

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.entity.Player
import java.sql.Connection
import java.util.*

/**
 * 数据库管理器
 * 用于管理玩家的计分板配置
 */
class DatabaseManager(val plugin: KaScoreboard) {
    var dataSource: HikariDataSource? = null

    /**
     * 初始化数据库
     */
    fun setup() {
        val config = HikariConfig()
        val dbType = plugin.config.getString("database.type", "SQLite") ?: "SQLite"

        if (dbType.equals("MySQL", ignoreCase = true)) {
            config.jdbcUrl = "jdbc:mysql://${plugin.config.getString("database.mysql.host")}:${plugin.config.getInt("database.mysql.port")}/${plugin.config.getString("database.mysql.database")}"
            config.username = plugin.config.getString("database.mysql.username")
            config.password = plugin.config.getString("database.mysql.password")

            // 连接池配置
            config.minimumIdle = plugin.config.getInt("database.mysql.pool.minimum-idle", 5)
            config.maximumPoolSize = plugin.config.getInt("database.mysql.pool.maximum-pool-size", 10)
            config.connectionTimeout = plugin.config.getLong("database.mysql.pool.connection-timeout", 30000)
            config.idleTimeout = plugin.config.getLong("database.mysql.pool.idle-timeout", 600000)
            config.maxLifetime = plugin.config.getLong("database.mysql.pool.max-lifetime", 1800000)

            config.driverClassName = "com.mysql.cj.jdbc.Driver"
        } else {
            val file = plugin.dataFolder.resolve(plugin.config.getString("database.sqlite.filename", "data.db") ?: "data.db")
            config.jdbcUrl = "jdbc:sqlite:${file.absolutePath}"
            config.driverClassName = "org.sqlite.JDBC"
            config.maximumPoolSize = 1
        }

        dataSource = HikariDataSource(config)
        createTables()
    }

    /**
     * 获取数据库连接
     */
    val connection: Connection
        get() = dataSource!!.connection

    /**
     * 创建数据库表
     */
    private fun createTables() {
        val dbType = plugin.config.getString("database.type", "sqlite") ?: "sqlite"
        val isMySQL = dbType.equals("mysql", ignoreCase = true)
        val autoIncrement = if (isMySQL) "AUTO_INCREMENT" else ""
        val uniqueConstraint = if (isMySQL) "UNIQUE KEY" else "UNIQUE"

        connection.use { conn ->
            val statement = conn.createStatement()

            // 玩家计分板配置表
            statement.execute("""
                CREATE TABLE IF NOT EXISTS player_scoreboards (
                    id INTEGER PRIMARY KEY $autoIncrement,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL,
                    scoreboard_name VARCHAR(64) NOT NULL,
                    update_time BIGINT,
                    $uniqueConstraint(player_uuid)
                )
            """)
        }
    }

    /**
     * 获取玩家的计分板名称
     */
    fun getPlayerScoreboard(playerUuid: UUID): String? {
        connection.use { conn ->
            val sql = "SELECT scoreboard_name FROM player_scoreboards WHERE player_uuid = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, playerUuid.toString())
                val resultSet = stmt.executeQuery()
                if (resultSet.next()) {
                    return resultSet.getString("scoreboard_name")
                }
            }
        }
        return null
    }

    /**
     * 获取玩家的计分板信息（包含玩家名称）
     */
    fun getPlayerScoreboardInfo(playerUuid: UUID): PlayerScoreboardInfo? {
        connection.use { conn ->
            val sql = "SELECT player_name, scoreboard_name FROM player_scoreboards WHERE player_uuid = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, playerUuid.toString())
                val resultSet = stmt.executeQuery()
                if (resultSet.next()) {
                    return PlayerScoreboardInfo(
                        resultSet.getString("player_name"),
                        resultSet.getString("scoreboard_name")
                    )
                }
            }
        }
        return null
    }

    /**
     * 设置玩家的计分板名称
     */
    fun setPlayerScoreboard(playerName: String, playerUuid: UUID, scoreboardName: String) {
        connection.use { conn ->
            val currentTime = System.currentTimeMillis()
            val sql = """
                INSERT INTO player_scoreboards (player_uuid, player_name, scoreboard_name, update_time)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    scoreboard_name = excluded.scoreboard_name,
                    update_time = excluded.update_time
            """.trimIndent()

            // MySQL语法不同
            val finalSql = if (plugin.config.getString("database.type", "sqlite")?.equals("mysql", ignoreCase = true) == true) {
                """
                    INSERT INTO player_scoreboards (player_uuid, player_name, scoreboard_name, update_time)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        player_name = VALUES(player_name),
                        scoreboard_name = VALUES(scoreboard_name),
                        update_time = VALUES(update_time)
                """.trimIndent()
            } else {
                sql
            }

            conn.prepareStatement(finalSql).use { stmt ->
                stmt.setString(1, playerUuid.toString())
                stmt.setString(2, playerName)
                stmt.setString(3, scoreboardName)
                stmt.setLong(4, currentTime)
                stmt.executeUpdate()
            }
        }
    }

    /**
     * 删除玩家的计分板配置
     */
    fun deletePlayerScoreboard(playerUuid: UUID) {
        connection.use { conn ->
            val sql = "DELETE FROM player_scoreboards WHERE player_uuid = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, playerUuid.toString())
                stmt.executeUpdate()
            }
        }
    }

    /**
     * 关闭数据库连接
     */
    fun close() {
        dataSource?.close()
        dataSource = null
    }
}

/**
 * 玩家计分板信息数据类
 */
data class PlayerScoreboardInfo(
    val playerName: String,
    val scoreboardName: String
)
