package org.katacr.kaScoreboard

import fr.mrmicky.fastboard.FastBoard
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.*

/**
 * 计分板处理器
 * 负责管理玩家的计分板显示和更新
 */
class ScoreboardHandler(private val plugin: KaScoreboard) {
    private val boards = mutableMapOf<UUID, ScoreboardData>()
    private var updateTask: BukkitTask? = null

    /**
     * 行配置数据类
     */
    data class LineConfig(
        val updateInterval: Long,  // 更新间隔（tick），-1 表示不更新
        val texts: List<String>,   // 文本列表（用于循环，条件判断时作为默认值）
        val conditions: List<Map<*, *>>, // 条件判断列表（每项包含 condition, allow, deny）
        var currentIndex: Int = 0, // 当前索引
        var lastUpdate: Long = 0,  // 上次更新时间
        var lastConditionCheck: Long = 0, // 上次条件检查时间
        var currentTextList: List<String> = emptyList(), // 当前使用的文本列表
        var lastRenderedText: String = "" // 上次渲染的文本（用于检查是否需要更新）
    ) {
        /**
         * 根据条件获取当前文本列表
         */
        private fun getCurrentTextList(player: Player): List<String> {
            // 如果有条件判断，根据条件获取文本列表
            if (conditions.isNotEmpty()) {
                val list = ConditionUtils.getConditionalListFromList(player, conditions, texts)
                if (list.isNotEmpty()) {
                    currentTextList = list
                    return list
                }
            }

            // 没有条件判断或条件列表为空，使用默认 texts
            currentTextList = texts
            return texts
        }

        /**
         * 根据条件获取当前文本
         */
        fun getCurrentText(player: Player): String {
            val textList = if (conditions.isNotEmpty()) {
                getCurrentTextList(player)
            } else {
                texts
            }

            return textList.getOrNull(currentIndex) ?: ""
        }

        fun updateIndex() {
            val targetList = if (currentTextList.isNotEmpty()) currentTextList else texts
            if (targetList.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % targetList.size
            }
        }

        fun shouldUpdate(currentTick: Long): Boolean {
            // 如果有条件判断，每10tick检查一次条件（避免每次都检查）
            if (conditions.isNotEmpty()) {
                return currentTick - lastConditionCheck >= 10
            }

            return updateInterval > 0 && currentTick - lastUpdate >= updateInterval
        }

        fun markUpdate() {
            val currentTick = System.currentTimeMillis() / 50
            lastUpdate = currentTick
            lastConditionCheck = currentTick
        }
    }

    /**
     * 为玩家显示计分板
     */
    fun showScoreboard(player: Player, scoreboardName: String): Boolean {
        // 获取计分板配置
        val config = plugin.scoreboardManager.getScoreboardConfig(scoreboardName)
            ?: run {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c计分板 '$scoreboardName' 不存在!"))
                return false
            }

        // 检查显示条件
        if (!checkConditions(player, config)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c你没有权限显示此计分板!"))
            return false
        }

        // 移除旧计分板
        hideScoreboard(player)

        // 解析标题配置
        val titleConfig = parseLineConfig(config, "title") ?: LineConfig(5, listOf("&a计分板"), emptyList())

        // 解析行配置
        val lineConfigs = mutableListOf<LineConfig>()
        val linesSection = config.getConfigurationSection("lines")
        if (linesSection != null) {
            linesSection.getKeys(false).forEach { lineName ->
                val lineConfig = parseLineConfig(config, "lines.$lineName")
                if (lineConfig != null) {
                    lineConfigs.add(lineConfig)
                }
            }
        }

        // 如果没有配置行，添加默认空行
        if (lineConfigs.isEmpty()) {
            lineConfigs.add(LineConfig(-1, listOf(""), emptyList()))
        }

        // 创建新计分板
        val board = FastBoard(player)

        // 初始化标题和内容
        val titleText = processText(titleConfig.getCurrentText(player), player)
        board.updateTitle(ChatColor.translateAlternateColorCodes('&', titleText))

        val displayLines = lineConfigs.map { lineConfig ->
            val rawText = lineConfig.getCurrentText(player)
            val processedText = ChatColor.translateAlternateColorCodes('&', processText(rawText, player))
            // 保存初始渲染的文本
            lineConfig.lastRenderedText = processedText
            processedText
        }
        board.updateLines(displayLines)

        // 保存到内存
        boards[player.uniqueId] = ScoreboardData(board, scoreboardName, titleConfig, lineConfigs, config, displayLines)

        // 保存到数据库
        plugin.databaseManager.setPlayerScoreboard(player.name, player.uniqueId, scoreboardName)

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a已显示计分板: $scoreboardName"))
        return true
    }

    /**
     * 解析行配置
     */
    private fun parseLineConfig(config: YamlConfiguration, path: String): LineConfig? {
        val updateInterval = config.getLong("$path.update", -1)
        val textList = config.getList("$path.text") as? List<*>

        if (textList.isNullOrEmpty()) return null

        // 检查是否为条件判断格式（第一个元素是 Map）
        val firstItem = textList.firstOrNull()
        val (texts, conditions) = if (firstItem is Map<*, *>) {
            // 条件判断格式：使用空列表作为默认 texts，实际值从条件中获取
            emptyList<String>() to textList.filterIsInstance<Map<*, *>>()
        } else {
            // 简单字符串列表格式
            textList.mapNotNull { it as? String } to emptyList<Map<*, *>>()
        }

        if (conditions.isEmpty() && texts.isEmpty()) return null

        return LineConfig(updateInterval, texts, conditions)
    }

    /**
     * 处理文本（替换变量）
     */
    private fun processText(text: String, player: Player): String {
        return if (plugin.config.getBoolean("placeholderapi", true)
            && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player,
                ChatColor.translateAlternateColorCodes('&', text))
        } else {
            ChatColor.translateAlternateColorCodes('&', text)
                .replace("%player_name%", player.name)
                .replace("%world%", player.world.name)
                .replace("%x%", player.location.blockX.toString())
                .replace("%y%", player.location.blockY.toString())
                .replace("%z%", player.location.blockZ.toString())
        }
    }

    /**
     * 隐藏玩家计分板
     */
    fun hideScoreboard(player: Player) {
        val data = boards.remove(player.uniqueId) ?: return

        data.board.delete()
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e已隐藏计分板"))
    }

    /**
     * 切换玩家计分板显示状态
     */
    fun toggleScoreboard(player: Player): Boolean {
        return if (boards.containsKey(player.uniqueId)) {
            hideScoreboard(player)
            false
        } else {
            // 显示默认计分板或上次使用的计分板
            val lastScoreboard = plugin.databaseManager.getPlayerScoreboard(player.uniqueId)
            if (lastScoreboard != null) {
                showScoreboard(player, lastScoreboard)
            } else {
                showScoreboard(player, "default")
            }
            true
        }
    }

    /**
     * 更新所有玩家的计分板
     */
    fun updateAllScoreboards() {
        val currentTick = System.currentTimeMillis() / 50 // 转换为 tick

        boards.values.forEach { data ->
            var titleUpdated = false
            var linesUpdated = false
            val newLines = data.currentLines.toMutableList()

            // 更新标题
            if (data.titleConfig.shouldUpdate(currentTick)) {
                data.titleConfig.updateIndex()
                val titleText = processText(data.titleConfig.getCurrentText(data.board.player), data.board.player)
                data.board.updateTitle(ChatColor.translateAlternateColorCodes('&', titleText))
                data.titleConfig.markUpdate()
                titleUpdated = true
            }

            // 独立更新每一行
            data.lineConfigs.forEachIndexed { index, lineConfig ->
                if (lineConfig.shouldUpdate(currentTick)) {
                    // 处理文本（替换变量）
                    val rawText = lineConfig.getCurrentText(data.board.player)
                    val processedText = ChatColor.translateAlternateColorCodes('&', processText(rawText, data.board.player))

                    // 检查文本是否发生变化
                    if (processedText != lineConfig.lastRenderedText) {
                        newLines[index] = processedText
                        lineConfig.lastRenderedText = processedText
                        linesUpdated = true
                    }

                    // 更新索引（用于循环显示）
                    lineConfig.updateIndex()
                    lineConfig.markUpdate()
                }
            }

            // 只有当有行需要更新时才更新计分板
            if (linesUpdated) {
                data.currentLines = newLines
                data.board.updateLines(newLines)
            }
        }
    }

    /**
     * 启动自动更新任务
     */
    fun startUpdateTask() {
        val interval = plugin.config.getLong("update-interval", 20)
        if (interval <= 0) return

        updateTask?.cancel()

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            updateAllScoreboards()
        }, 0L, interval)
    }

    /**
     * 停止自动更新任务
     */
    fun stopUpdateTask() {
        updateTask?.cancel()
        updateTask = null
    }

    /**
     * 处理玩家世界切换
     */
    fun onPlayerWorldChange(player: Player) {
        val data = boards[player.uniqueId] ?: return

        // 检查当前世界是否仍然满足显示条件
        val config = plugin.scoreboardManager.getScoreboardConfig(data.scoreboardName) ?: return
        if (!checkConditions(player, config)) {
            hideScoreboard(player)
        }
    }

    /**
     * 处理玩家退出
     */
    fun onPlayerQuit(player: Player) {
        val data = boards.remove(player.uniqueId) ?: return
        data.board.delete()
    }

    /**
     * 检查显示条件
     */
    private fun checkConditions(player: Player, config: YamlConfiguration): Boolean {
        if (!config.contains("conditions")) return true

        val conditions = config.getList("conditions") ?: return true

        conditions.forEach { condition ->
            if (condition is Map<*, *>) {
                val type = condition["type"] as? String ?: return@forEach

                when (type.lowercase()) {
                    "permission" -> {
                        val permission = condition["permission"] as? String
                        if (permission != null && !player.hasPermission(permission)) {
                            return false
                        }
                    }
                    "world" -> {
                        val worlds = condition["worlds"] as? List<*>
                        if (worlds != null && player.world.name !in worlds.map { it.toString() }) {
                            return false
                        }
                    }
                }
            }
        }

        return true
    }

    /**
     * 清除所有计分板
     */
    fun clearAll() {
        boards.values.forEach { data ->
            data.board.delete()
        }
        boards.clear()
        stopUpdateTask()
    }

    /**
     * 获取玩家当前使用的计分板
     */
    fun getPlayerScoreboard(player: Player): String? {
        return boards[player.uniqueId]?.scoreboardName
    }

    /**
     * 检查玩家是否显示计分板
     */
    fun hasScoreboard(player: Player): Boolean {
        return boards.containsKey(player.uniqueId)
    }

    /**
     * 计分板数据类
     */
    data class ScoreboardData(
        val board: FastBoard,
        val scoreboardName: String,
        val titleConfig: LineConfig,
        val lineConfigs: List<LineConfig>,
        val config: YamlConfiguration,
        var currentLines: List<String> = emptyList() // 当前显示的行列表
    )
}
