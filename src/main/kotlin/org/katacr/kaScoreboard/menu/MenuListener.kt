package org.katacr.kaScoreboard.menu

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.katacr.kaScoreboard.KaScoreboard

/**
 * 菜单监听器
 */
class MenuListener(
    private val plugin: KaScoreboard
) : Listener {

    /**
     * 监听玩家切换快捷栏（模拟滚轮）
     */
    @EventHandler
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        val playerId = player.uniqueId

        // 检查玩家是否在菜单中
        val state = plugin.menuManager.getPlayerState(playerId) ?: return

        // 判断滚轮方向
        val newSlot = event.newSlot
        val previousSlot = event.previousSlot

        // 计算滚轮方向
        // 正常滚动：0→1, 1→2, ..., 7→8 → scrollDown()（往下滚动）
        //          1→0, 2→1, ..., 8→7 → scrollUp()（往上滚动）
        // 特殊跨越边界：8→0 → scrollDown()（跨越边界往下滚动）
        //                0→8 → scrollUp()（跨越边界往上滚动）

        var scrolled = false

        when {
            // 特殊跨越边界：8→0 是往下滚轮一次
            previousSlot == 8 && newSlot == 0 -> {
                scrolled = state.scrollDown()
            }
            // 特殊跨越边界：0→8 是往上滚轮一次
            previousSlot == 0 && newSlot == 8 -> {
                scrolled = state.scrollUp()
            }
            // 正常往下滚动：0→1, 1→2, ..., 7→8
            newSlot > previousSlot -> {
                scrolled = state.scrollDown()
            }
            // 正常往上滚动：1→0, 2→1, ..., 8→7
            newSlot < previousSlot -> {
                scrolled = state.scrollUp()
            }
        }

        // 如果发生了滚动，立即刷新菜单
        if (scrolled) {
            plugin.menuHandler.updateMenu(player)
            event.isCancelled = true  // 取消快捷栏切换
        }
    }

    /**
     * 监听玩家点击（左键/右键）
     */
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val playerId = player.uniqueId

        // 检查玩家是否在菜单中
        val state = plugin.menuManager.getPlayerState(playerId) ?: return

        val action = event.action
        val selectedButton = state.getSelectedButton()

        when (action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> {
                // 左键：选择当前按钮
                if (selectedButton != null) {
                    handleButtonSelect(player, state, selectedButton)
                }
                event.isCancelled = true
            }
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> {
                // 右键：返回/关闭菜单
                handleCloseMenu(player, state)
                event.isCancelled = true
            }
            else -> {
                // 其他操作不处理
            }
        }
    }

    /**
     * 处理按钮选择
     */
    private fun handleButtonSelect(player: Player, state: PlayerMenuState, button: MenuButtonConfig) {
        // 这里可以根据按钮ID执行不同的操作
        // 例如：打开子菜单、执行命令、传送玩家等

        // TODO: 实现按钮操作逻辑
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a你选择了: ${button.id}"))

        // 可以根据按钮的ID执行不同操作
        when (button.id) {
            "button-1" -> {
                // 示例：打开传送菜单
                // plugin.menuHandler.openMenu(player, "teleport_menu")
            }
            "button-2" -> {
                // 示例：打开商店
                // plugin.menuHandler.openMenu(player, "shop_menu")
            }
            // 其他按钮...
        }
    }

    /**
     * 处理关闭菜单
     */
    private fun handleCloseMenu(player: Player, state: PlayerMenuState) {
        // 关闭菜单，恢复普通计分板
        plugin.menuHandler.closeMenu(player)

        // 显示消息
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e已关闭菜单"))
    }

    /**
     * 监听玩家退出
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerId = event.player.uniqueId
        // 清除玩家菜单状态
        plugin.menuManager.removePlayerState(playerId)
    }
}
