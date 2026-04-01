package org.katacr.kaScoreboard.menu

import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * 玩家菜单状态
 */
data class PlayerMenuState(
    val playerId: UUID,                // 玩家UUID
    val menuId: String,                 // 当前菜单ID
    val menuConfig: MenuConfig,          // 菜单配置
    var selectedIndex: Int,              // 选中按钮索引（全局索引）
    var windowStart: Int,               // 窗口起始索引
    var textIndices: MutableMap<String, Int> = mutableMapOf(),  // 各行文本索引（用于循环显示）
    var lastUpdate: Long = 0           // 上次更新时间
) {
    /**
     * 获取窗口大小
     */
    fun getWindowSize(): Int {
        return menuConfig.getMaxVisibleButtons()
    }

    /**
     * 获取总按钮数
     */
    fun getTotalButtons(): Int {
        return menuConfig.buttons.size
    }

    /**
     * 判断是否在窗口内
     */
    fun isInWindow(): Boolean {
        return selectedIndex in windowStart until min(windowStart + getWindowSize(), getTotalButtons())
    }

    /**
     * 获取当前窗口内的可见按钮
     */
    fun getVisibleButtons(): List<MenuButtonConfig> {
        val end = min(windowStart + getWindowSize(), getTotalButtons())
        if (windowStart >= end) return emptyList()
        return menuConfig.buttons.subList(windowStart, end)
    }

    /**
     * 获取当前选中的按钮
     */
    fun getSelectedButton(): MenuButtonConfig? {
        if (selectedIndex < 0 || selectedIndex >= getTotalButtons()) return null
        return menuConfig.buttons.getOrNull(selectedIndex)
    }

    /**
     * 向下滚动
     */
    fun scrollDown(): Boolean {
        if (getTotalButtons() == 0) return false

        val oldSelectedIndex = selectedIndex
        val oldWindowStart = windowStart

        // 增加选中索引
        selectedIndex += 1

        when (menuConfig.scrollMode) {
            ScrollMode.LOOP -> {
                // 循环模式：超出则回到开头
                if (selectedIndex >= getTotalButtons()) {
                    selectedIndex = 0
                    windowStart = 0
                } else if (!isInWindow()) {
                    // 窗口向上滚动（窗口向下移动）
                    windowStart = selectedIndex - getWindowSize() + 1
                }
            }
            ScrollMode.CLAMP -> {
                // 边界模式：超出则停止
                if (selectedIndex >= getTotalButtons()) {
                    selectedIndex = getTotalButtons() - 1
                    return false  // 没有实际滚动
                }
                if (!isInWindow()) {
                    // 窗口向上滚动（窗口向下移动）
                    windowStart = selectedIndex - getWindowSize() + 1
                }
            }
        }

        return selectedIndex != oldSelectedIndex || windowStart != oldWindowStart
    }

    /**
     * 向上滚动
     */
    fun scrollUp(): Boolean {
        if (getTotalButtons() == 0) return false

        val oldSelectedIndex = selectedIndex
        val oldWindowStart = windowStart

        // 减少选中索引
        selectedIndex -= 1

        when (menuConfig.scrollMode) {
            ScrollMode.LOOP -> {
                // 循环模式：超出则跳到末尾
                if (selectedIndex < 0) {
                    selectedIndex = getTotalButtons() - 1
                    windowStart = max(0, selectedIndex - getWindowSize() + 1)
                } else if (!isInWindow()) {
                    // 窗口向下滚动（窗口向上移动）
                    windowStart = selectedIndex
                }
            }
            ScrollMode.CLAMP -> {
                // 边界模式：超出则停止
                if (selectedIndex < 0) {
                    selectedIndex = 0
                    return false  // 没有实际滚动
                }
                if (!isInWindow()) {
                    // 窗口向下滚动（窗口向上移动）
                    windowStart = selectedIndex
                }
            }
        }

        return selectedIndex != oldSelectedIndex || windowStart != oldWindowStart
    }

    /**
     * 获取文本索引（用于循环显示）
     */
    fun getTextIndex(key: String): Int {
        return textIndices.getOrDefault(key, 0)
    }

    /**
     * 更新文本索引
     */
    fun updateTextIndex(key: String, size: Int) {
        val currentIndex = getTextIndex(key)
        textIndices[key] = (currentIndex + 1) % size
    }
}

/**
 * 菜单管理器
 */
class MenuManager {

    private val playerStates = mutableMapOf<UUID, PlayerMenuState>()

    /**
     * 设置玩家菜单状态
     */
    fun setPlayerState(playerId: UUID, state: PlayerMenuState) {
        playerStates[playerId] = state
    }

    /**
     * 获取玩家菜单状态
     */
    fun getPlayerState(playerId: UUID): PlayerMenuState? {
        return playerStates[playerId]
    }

    /**
     * 移除玩家菜单状态
     */
    fun removePlayerState(playerId: UUID) {
        playerStates.remove(playerId)
    }

    /**
     * 检查玩家是否在菜单中
     */
    fun isPlayerInMenu(playerId: UUID): Boolean {
        return playerStates.containsKey(playerId)
    }

    /**
     * 获取所有在线玩家的菜单状态
     */
    fun getAllPlayerStates(): Collection<PlayerMenuState> {
        return playerStates.values
    }

    /**
     * 清除所有菜单状态
     */
    fun clearAll() {
        playerStates.clear()
    }
}
