package org.katacr.kaScoreboard

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * 计分板事件监听器
 */
class ScoreboardListener(private val plugin: KaScoreboard) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // 检查是否自动显示
        if (!plugin.config.getBoolean("auto-show", true)) {
            return
        }

        // 获取玩家上次使用的计分板
        val lastScoreboard = plugin.databaseManager.getPlayerScoreboard(player.uniqueId)
        val scoreboardName = lastScoreboard ?: "default"

        // 显示计分板
        plugin.scoreboardHandler.showScoreboard(player, scoreboardName)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.scoreboardHandler.onPlayerQuit(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        plugin.scoreboardHandler.onPlayerWorldChange(event.player)
    }
}
