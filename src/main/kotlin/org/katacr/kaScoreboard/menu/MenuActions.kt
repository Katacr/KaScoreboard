package org.katacr.kaScoreboard.menu

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.ChatColor
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.katacr.kaScoreboard.ConditionUtils
import org.katacr.kaScoreboard.KaScoreboard
import java.util.concurrent.CompletableFuture
import com.comphenix.protocol.*
import com.comphenix.protocol.wrappers.*
import me.clip.placeholderapi.PlaceholderAPI

/**
 * 菜单动作处理器
 * 负责解析和执行菜单中的各种动作
 */
object MenuActions {
    private var plugin: KaScoreboard? = null

    /**
     * 设置插件引用
     */
    fun init(kascoreboard: KaScoreboard) {
        plugin = kascoreboard
    }

    /**
     * 发送 ActionBar 消息（ProtocolLib 版本）
     * 需要安装 ProtocolLib 插件
     */
    private fun sendActionBar(player: Player, message: String) {
        // 检查 ProtocolLib 是否可用
        if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            plugin?.logger?.severe("ActionBar 功能需要 ProtocolLib 插件！请安装 ProtocolLib。")
            return
        }

        try {
            val protocolManager = ProtocolLibrary.getProtocolManager()

            // 在 ProtocolLib 1.17+ 中，TITLE 包被拆分为独立的包
            // 优先使用新的 SET_ACTION_BAR_TEXT 包
            val packet = try {
                protocolManager.createPacket(PacketType.Play.Server.SET_ACTION_BAR_TEXT)
            } catch (e: Exception) {
                // 回退到旧的 TITLE 包（1.16及以下）
                protocolManager.createPacket(PacketType.Play.Server.TITLE)
            }

            // 设置消息内容
            val component = WrappedChatComponent.fromText(message)

            try {
                // 新版本的 SET_ACTION_BAR_TEXT 包使用直接的消息字段
                packet.chatComponents.write(0, component)
            } catch (e: Exception) {
                // 旧版本的 TITLE 包需要设置 TitleAction 和消息
                val action = EnumWrappers.TitleAction.ACTIONBAR
                packet.titleActions.write(0, action)
                packet.chatComponents.write(0, component)
            }

            // 发送数据包
            protocolManager.sendServerPacket(player, packet)
        } catch (e: Exception) {
            plugin?.logger?.warning("发送 ActionBar 失败: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 解析并发送标题
     * 格式: title=主标题;subtitle=副标题;in=淡入时长;keep=停留时长;out=淡出时长
     */
    private fun sendTitle(player: Player, args: String) {
        // 检查 ProtocolLib 是否可用
        if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            plugin?.logger?.severe("Title 功能需要 ProtocolLib 插件！请安装 ProtocolLib。")
            return
        }

        var title = ""
        var subtitle = ""
        var fadeIn = 10   // 默认淡入 0.5 秒（10 ticks）
        var stay = 70     // 默认停留 3.5 秒（70 ticks）
        var fadeOut = 20  // 默认淡出 1 秒（20 ticks）

        // 解析参数
        args.split(";").forEach { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().lowercase()
                val value = parts[1].trim()
                when (key) {
                    "title" -> title = value
                    "subtitle" -> subtitle = value
                    "in" -> fadeIn = value.toIntOrNull()?.coerceAtLeast(0) ?: fadeIn
                    "keep" -> stay = value.toIntOrNull()?.coerceAtLeast(0) ?: stay
                    "out" -> fadeOut = value.toIntOrNull()?.coerceAtLeast(0) ?: fadeOut
                }
            }
        }

        try {
            val protocolManager = ProtocolLibrary.getProtocolManager()

            // 尝试使用新版本协议（1.17+）
            val isNewVersion = try {
                protocolManager.createPacket(PacketType.Play.Server.SET_TITLE_TEXT)
                true
            } catch (e: Exception) {
                false
            }

            if (isNewVersion) {
                // 新版本：使用独立的数据包
                // 1. 设置副标题（如果有）
                if (subtitle.isNotEmpty()) {
                    val subtitlePacket = protocolManager.createPacket(PacketType.Play.Server.SET_SUBTITLE_TEXT)
                    val subtitleComponent = WrappedChatComponent.fromText(ChatColor.translateAlternateColorCodes('&', subtitle))
                    subtitlePacket.chatComponents.write(0, subtitleComponent)
                    protocolManager.sendServerPacket(player, subtitlePacket)
                }

                // 2. 设置动画时间
                val timesPacket = protocolManager.createPacket(PacketType.Play.Server.SET_TITLES_ANIMATION)
                timesPacket.integers.write(0, fadeIn)
                timesPacket.integers.write(1, stay)
                timesPacket.integers.write(2, fadeOut)
                protocolManager.sendServerPacket(player, timesPacket)

                // 3. 设置主标题并显示
                if (title.isNotEmpty()) {
                    val titlePacket = protocolManager.createPacket(PacketType.Play.Server.SET_TITLE_TEXT)
                    val titleComponent = WrappedChatComponent.fromText(ChatColor.translateAlternateColorCodes('&', title))
                    titlePacket.chatComponents.write(0, titleComponent)
                    protocolManager.sendServerPacket(player, titlePacket)
                }
            } else {
                // 旧版本（1.16及以下）：使用单一的 TITLE 包
                // 1. 设置副标题
                if (subtitle.isNotEmpty()) {
                    val subtitlePacket = protocolManager.createPacket(PacketType.Play.Server.TITLE)
                    subtitlePacket.titleActions.write(0, EnumWrappers.TitleAction.SUBTITLE)
                    val subtitleComponent = WrappedChatComponent.fromText(ChatColor.translateAlternateColorCodes('&', subtitle))
                    subtitlePacket.chatComponents.write(0, subtitleComponent)
                    protocolManager.sendServerPacket(player, subtitlePacket)
                }

                // 2. 设置动画时间
                val timesPacket = protocolManager.createPacket(PacketType.Play.Server.TITLE)
                timesPacket.titleActions.write(0, EnumWrappers.TitleAction.TIMES)
                timesPacket.integers.write(0, fadeIn)
                timesPacket.integers.write(1, stay)
                timesPacket.integers.write(2, fadeOut)
                protocolManager.sendServerPacket(player, timesPacket)

                // 3. 设置主标题并显示
                if (title.isNotEmpty()) {
                    val titlePacket = protocolManager.createPacket(PacketType.Play.Server.TITLE)
                    titlePacket.titleActions.write(0, EnumWrappers.TitleAction.TITLE)
                    val titleComponent = WrappedChatComponent.fromText(ChatColor.translateAlternateColorCodes('&', title))
                    titlePacket.chatComponents.write(0, titleComponent)
                    protocolManager.sendServerPacket(player, titlePacket)
                }
            }
        } catch (e: Exception) {
            plugin?.logger?.warning("发送 Title 失败: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 解析变量（PAPI）
     */
    private fun resolveVariables(player: Player, text: String): String {
        var result = text

        // PlaceholderAPI 变量替换
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                result = PlaceholderAPI.setPlaceholders(player, result)
            } catch (_: Exception) {
                // PAPI 解析失败，忽略
            }
        }

        return result
    }

    /**
     * 执行动作列表
     * @param player 玩家对象
     * @param actionList 动作列表
     * @param menuOpener 菜单打开函数
     * @param config 菜单配置（用于条件判断）
     */
    fun executeActionList(
        player: Player,
        actionList: List<*>,
        menuOpener: (Player, String) -> Unit,
        config: MenuConfig? = null
    ): CompletableFuture<Void> {
        // 创建异步执行链
        var chain: CompletableFuture<Void> = CompletableFuture.completedFuture(null)

        for (action in actionList) {
            chain = chain.thenCompose { _ ->
                when (action) {
                    is Map<*, *> -> {
                        // 条件判断动作
                        val group = action
                        var conditionStr = group["condition"] as? String ?: ""

                        // 替换条件中的变量
                        conditionStr = resolveVariables(player, conditionStr)

                        val successActions = (group["actions"] ?: group["allow"]) as? List<*> ?: emptyList<Any>()
                        val denyActions = (group["deny"] as? List<*>) ?: emptyList<Any>()

                        val actionsToUse = if (ConditionUtils.checkCondition(player, conditionStr)) {
                            successActions
                        } else {
                            denyActions
                        }

                        // 递归执行子动作列表
                        executeActionList(player, actionsToUse.map { it ?: Any() }, menuOpener, config)
                    }
                    is List<*> -> {
                        // 普通动作列表 - 按顺序执行
                        var subChain: CompletableFuture<Void> = CompletableFuture.completedFuture(null)

                        for (subAction in action) {
                            val actionStr = subAction?.toString() ?: continue

                            val future = CompletableFuture<Void>()
                            subChain = subChain.thenCompose { _ ->
                                executeSingleAction(player, actionStr, menuOpener, config)
                                future.complete(null)
                                future
                            }
                        }
                        subChain
                    }
                    is String -> {
                        // 单个动作字符串
                        val future = CompletableFuture<Void>()
                        executeSingleAction(player, action, menuOpener, config)
                        future.complete(null)
                        future
                    }
                    else -> {
                        // 忽略其他类型
                        CompletableFuture.completedFuture(null)
                    }
                }
            }
        }

        // 返回异步执行链，并添加异常处理
        return chain.exceptionally { error ->
            plugin?.logger?.severe("动作执行失败: ${error.message}")
            error.printStackTrace()
            null
        }
    }

    /**
     * 执行单个动作
     */
    private fun executeSingleAction(
        player: Player,
        action: String,
        menuOpener: (Player, String) -> Unit,
        config: MenuConfig? = null
    ) {
        var finalCmd = action

        // 解析变量
        finalCmd = resolveVariables(player, finalCmd)

        when {
            // tell: 普通消息
            finalCmd.startsWith("tell:", ignoreCase = true) -> {
                val message = finalCmd.removePrefix("tell:").trim()
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message))
            }

            // actionbar: ActionBar 消息
            finalCmd.startsWith("actionbar:", ignoreCase = true) -> {
                val message = finalCmd.removePrefix("actionbar:").trim()
                sendActionBar(player, ChatColor.translateAlternateColorCodes('&', message))
            }

            // title: 标题消息
            finalCmd.startsWith("title:", ignoreCase = true) -> {
                val args = finalCmd.removePrefix("title:").trim()
                sendTitle(player, args)
            }

            // command: 玩家执行指令（需要主线程）
            finalCmd.startsWith("command:", ignoreCase = true) -> {
                val cmd = finalCmd.removePrefix("command:").trim()
                Bukkit.getScheduler().runTask(plugin ?: return, Runnable {
                    player.performCommand(cmd)
                })
            }

            // chat: 玩家发送聊天消息（需要主线程）
            finalCmd.startsWith("chat:", ignoreCase = true) -> {
                val msg = finalCmd.removePrefix("chat:").trim()
                Bukkit.getScheduler().runTask(plugin ?: return, Runnable {
                    player.chat(msg)
                })
            }

            // console: 控制台执行指令（需要主线程）
            finalCmd.startsWith("console:", ignoreCase = true) -> {
                val cmd = finalCmd.removePrefix("console:").trim()
                Bukkit.getScheduler().runTask(plugin ?: return, Runnable {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                })
            }

            // sound: 播放声音（需要主线程）
            finalCmd.startsWith("sound:", ignoreCase = true) -> {
                val args = finalCmd.removePrefix("sound:").trim()
                Bukkit.getScheduler().runTask(plugin ?: return, Runnable {
                    parseAndPlaySound(player, args)
                })
            }

            // open: 打开另一个菜单（需要主线程）
            finalCmd.startsWith("open:", ignoreCase = true) -> {
                val menuName = finalCmd.removePrefix("open:").trim()
                Bukkit.getScheduler().runTask(plugin ?: return, Runnable {
                    menuOpener(player, menuName)
                })
            }

            // close: 关闭菜单（需要主线程）
            finalCmd.trim().equals("close", ignoreCase = true) -> {
                Bukkit.getScheduler().runTask(plugin ?: return, Runnable {
                    plugin?.menuHandler?.closeMenu(player)
                })
            }

            // wait: 延迟执行（ticks）
            finalCmd.startsWith("wait:", ignoreCase = true) -> {
                val waitTime = finalCmd.removePrefix("wait:").trim().toLongOrNull() ?: 0L
                if (waitTime > 0) {
                    Thread.sleep(waitTime * 50)  // 转换为毫秒
                }
            }

            // return: 中断执行
            finalCmd.trim().equals("return", ignoreCase = true) -> {
                // 跳过后续动作
            }
        }
    }

    /**
     * 解析并发送声音
     * 格式: sound_name;volume=1.0;pitch=1.0;category=master
     */
    private fun parseAndPlaySound(player: Player, args: String) {
        var soundName = ""
        var volume = 1f
        var pitch = 1f
        var category = SoundCategory.MASTER

        val params = args.split(";")
        for (param in params) {
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().lowercase()
                val value = parts[1].trim()
                when (key) {
                    "volume" -> volume = value.toFloatOrNull() ?: 1f
                    "pitch" -> pitch = value.toFloatOrNull() ?: 1f
                    "category" -> category = value.lowercase().let { cat ->
                        when (cat) {
                            "master" -> SoundCategory.MASTER
                            "music" -> SoundCategory.MUSIC
                            "record" -> SoundCategory.RECORDS
                            "weather" -> SoundCategory.WEATHER
                            "block" -> SoundCategory.BLOCKS
                            "hostile" -> SoundCategory.HOSTILE
                            "neutral" -> SoundCategory.NEUTRAL
                            "player" -> SoundCategory.PLAYERS
                            "ambient" -> SoundCategory.AMBIENT
                            "voice" -> SoundCategory.VOICE
                            else -> SoundCategory.MASTER
                        }
                    }
                    else -> soundName = soundName.ifEmpty { parts[0].trim() }
                }
            } else if (parts.size == 1 && param.trim().isNotEmpty()) {
                if (soundName.isEmpty()) soundName = param.trim()
            }
        }

        if (soundName.isNotEmpty()) {
            try {
                val sound = Sound.valueOf(soundName.uppercase())
                player.playSound(player.location, sound, category, volume, pitch)
            } catch (e: IllegalArgumentException) {
                plugin?.logger?.warning("未知的声音: $soundName")
            }
        }
    }
}
