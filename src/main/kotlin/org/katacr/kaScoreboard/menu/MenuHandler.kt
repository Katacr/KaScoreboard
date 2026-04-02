package org.katacr.kaScoreboard.menu

import fr.mrmicky.fastboard.FastBoard
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.katacr.kaScoreboard.ConditionUtils as CU
import org.katacr.kaScoreboard.KaScoreboard
import java.io.File
import java.util.*

/**
 * 菜单处理器
 */
class MenuHandler(
    private val plugin: KaScoreboard
) {
    private val menuConfigs = mutableMapOf<String, MenuConfig>()
    private val playerBoards = mutableMapOf<UUID, PlayerMenuBoard>()
    private var updateTask: BukkitTask? = null

    /**
     * 加载所有菜单配置
     */
    fun loadMenus() {
        val menuFolder = File(plugin.dataFolder, "menus")
        if (!menuFolder.exists()) {
            menuFolder.mkdirs()
            // 释放默认菜单
            saveDefaultMenu(menuFolder, "main_menu.yml")
        }

        // 加载所有菜单文件
        menuFolder.listFiles { file ->
            file.isFile && file.extension == "yml"
        }?.forEach { file ->
            val menuId = file.nameWithoutExtension
            val config = YamlConfiguration.loadConfiguration(file)
            val menuConfig = MenuConfigParser.parse(menuId, config)
            if (menuConfig != null) {
                menuConfigs[menuId] = menuConfig
                plugin.logger.info("已加载菜单: $menuId")
            }
        }

        plugin.logger.info("已加载 ${menuConfigs.size} 个菜单")
    }

    /**
     * 保存默认菜单
     */
    private fun saveDefaultMenu(folder: File, filename: String) {
        try {
            val inputStream = plugin.javaClass.getResourceAsStream("/menus/$filename")
            if (inputStream != null) {
                val targetFile = File(folder, filename)
                if (!targetFile.exists()) {
                    inputStream.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    plugin.logger.info("已释放默认菜单: $filename")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("释放默认菜单时出错: ${e.message}")
        }
    }

    /**
     * 打开菜单
     */
    fun openMenu(player: Player, menuId: String): Boolean {
        // 获取菜单配置
        val menuConfig = menuConfigs[menuId] ?: run {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c菜单 '$menuId' 不存在!"))
            return false
        }

        // 检查菜单是否有效
        if (menuConfig.buttons.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c该菜单没有可用的按钮!"))
            return false
        }

        // 隐藏普通计分板（如果正在显示）
        if (plugin.scoreboardHandler.hasScoreboard(player)) {
            plugin.scoreboardHandler.hideScoreboard(player)
        }

        // 创建菜单计分板
        val board = FastBoard(player)
        val menuState = PlayerMenuState(
            playerId = player.uniqueId,
            menuId = menuId,
            menuConfig = menuConfig,
            selectedIndex = 0,
            windowStart = 0
        )

        // 保存玩家菜单状态
        plugin.menuManager.setPlayerState(player.uniqueId, menuState)

        // 渲染菜单
        renderMenu(player, board, menuState)

        // 保存玩家计分板引用
        playerBoards[player.uniqueId] = PlayerMenuBoard(board, menuState)

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a已打开菜单: ${menuConfig.name}"))
        return true
    }

    /**
     * 关闭菜单
     */
    fun closeMenu(player: Player) {
        val playerId = player.uniqueId
        val menuBoard = playerBoards.remove(playerId) ?: return

        // 删除计分板
        menuBoard.board.delete()

        // 清除菜单状态
        plugin.menuManager.removePlayerState(playerId)

        // 恢复普通计分板（可选）
        // plugin.scoreboardHandler.toggleScoreboard(player)
    }

    /**
     * 更新菜单
     */
    fun updateMenu(player: Player) {
        val playerId = player.uniqueId
        val menuBoard = playerBoards[playerId] ?: return

        // 重新渲染菜单
        renderMenu(player, menuBoard.board, menuBoard.menuState)
    }

    /**
     * 渲染菜单到计分板
     */
    private fun renderMenu(player: Player, board: FastBoard, state: PlayerMenuState) {
        val config = state.menuConfig
        val lines = mutableListOf<String>()

        // 1. 添加标题
        val titleText = renderLine(config.titleConfig, player, state)
        board.updateTitle(ChatColor.translateAlternateColorCodes('&', titleText))

        // 2. 添加头部
        config.headerConfigs.forEach { headerConfig ->
            val lineText = renderLine(headerConfig, player, state)
            if (lineText.isNotEmpty()) {
                lines.add(ChatColor.translateAlternateColorCodes('&', lineText))
            }
        }

        // 3. 添加按钮
        val visibleButtons = state.getVisibleButtons()

        visibleButtons.forEach { button ->
            val isSelected = state.menuConfig.buttons.indexOf(button) == state.selectedIndex
            val buttonText = renderButton(button, isSelected, player, state)
            if (buttonText.isNotEmpty()) {
                lines.add(ChatColor.translateAlternateColorCodes('&', buttonText))
            }
        }

        // 4. 添加底部
        config.footerConfigs.forEach { footerConfig ->
            val lineText = renderLine(footerConfig, player, state)
            if (lineText.isNotEmpty()) {
                lines.add(ChatColor.translateAlternateColorCodes('&', lineText))
            }
        }

        // 更新计分板内容
        board.updateLines(lines)
    }

    /**
     * 渲染行（用于header/footer/title）
     */
    private fun renderLine(
        lineConfig: MenuLineConfig,
        player: Player,
        state: PlayerMenuState
    ): String {
        // 处理条件判断
        if (lineConfig.conditions.isNotEmpty()) {
            val text = CU.getConditionalValueOrListFromList(
                player,
                lineConfig.conditions,
                lineConfig.texts.firstOrNull() ?: ""
            )
            return processText(text, player)
        }

        // 无条件判断，使用循环文本
        val key = "line_${lineConfig.hashCode()}"  // 生成唯一键
        val index = state.getTextIndex(key)
        val text = lineConfig.getText(index)
        return processText(text, player)
    }

    /**
     * 渲染按钮
     */
    private fun renderButton(
        button: MenuButtonConfig,
        isSelected: Boolean,
        player: Player,
        state: PlayerMenuState
    ): String {
        val key = "button_${button.id}"
        var index = state.getTextIndex(key)

        // 获取对应状态的条件和文本
        val conditions = button.getConditions(isSelected)
        val textList = button.getTextList(isSelected)

        // 处理条件判断
        if (conditions.isNotEmpty()) {
            // 从条件列表中获取符合条件的文本列表
            val conditionalTextList = mutableListOf<String>()

            for (condition in conditions) {
                if (condition is Map<*, *>) {
                    val condStr = condition["condition"] as? String
                    val allow = condition["allow"]
                    val deny = condition["deny"]

                    // 检查条件
                    val conditionMet = if (condStr != null) {
                        CU.checkCondition(player, condStr)
                    } else {
                        false
                    }

                    // 根据条件获取文本
                    val target = if (conditionMet) allow else deny

                    // 提取文本（支持字符串和列表）
                    when (target) {
                        is String -> if (target.isNotEmpty()) conditionalTextList.add(target)
                        is List<*> -> {
                            val strings = target.filterIsInstance<String>()
                            conditionalTextList.addAll(strings)
                        }
                    }

                    // 如果找到了文本，停止检查其他条件
                    if (conditionalTextList.isNotEmpty()) break
                }
            }

            // 获取文本（使用索引循环显示）
            val text = if (conditionalTextList.isNotEmpty()) {
                val effectiveIndex = index % conditionalTextList.size
                conditionalTextList[effectiveIndex]
            } else {
                // 如果条件判断列表为空，尝试从textList获取
                textList.getOrNull(index) ?: ""
            }

            // 更新索引（如果需要循环）
            val effectiveSize = if (conditionalTextList.isNotEmpty()) conditionalTextList.size else textList.size
            if (button.updateInterval > 0 && effectiveSize > 1) {
                state.updateTextIndex(key, effectiveSize)
            }

            return processText(text, player)
        }

        // 无条件判断
        val text = textList.getOrNull(index) ?: ""

        // 更新索引（如果需要循环）
        if (button.updateInterval > 0 && textList.isNotEmpty()) {
            state.updateTextIndex(key, textList.size)
        }

        return processText(text, player)
    }

    /**
     * 处理文本（替换变量和颜色）
     */
    private fun processText(text: String, player: Player): String {
        // PlaceholderAPI 变量替换
        return if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text)
            } catch (e: Exception) {
                text
            }
        } else {
            text
        }
    }

    /**
     * 启动自动更新任务
     */
    fun startUpdateTask() {
        val interval = plugin.config.getLong("menu-update-interval", 10)
        if (interval <= 0) return

        updateTask?.cancel()

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            updateAllMenus()
        }, 0L, interval)
    }

    /**
     * 更新所有菜单
     */
    private fun updateAllMenus() {
        val currentTick = System.currentTimeMillis() / 50

        playerBoards.values.forEach { menuBoard ->
            val state = menuBoard.menuState

            // 检查是否需要更新
            if (currentTick - state.lastUpdate >= 1) {  // 至少1tick才更新
                renderMenu(menuBoard.board.player, menuBoard.board, state)
                state.lastUpdate = currentTick
            }
        }
    }

    /**
     * 停止自动更新任务
     */
    fun stopUpdateTask() {
        updateTask?.cancel()
        updateTask = null
    }

    /**
     * 清除所有菜单
     */
    fun clearAll() {
        playerBoards.values.forEach { menuBoard ->
            menuBoard.board.delete()
        }
        playerBoards.clear()
        plugin.menuManager.clearAll()
        stopUpdateTask()
    }

    /**
     * 重载所有菜单
     */
    fun reload(): Int {
        menuConfigs.clear()
        loadMenus()
        return menuConfigs.size
    }

    /**
     * 获取所有菜单ID
     */
    fun getAllMenuIds(): List<String> {
        return menuConfigs.keys.toList()
    }

    /**
     * 玩家菜单计分板数据
     */
    data class PlayerMenuBoard(
        val board: FastBoard,
        val menuState: PlayerMenuState
    )
}
