package org.katacr.kaScoreboard.menu

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.katacr.kaScoreboard.ConditionUtils
import org.katacr.kaScoreboard.KaScoreboard
import java.util.concurrent.CompletableFuture

/**
 * 菜单动作处理器
 * 负责解析和执行菜单中的各种动作
 */
object MenuActions {
    private var plugin: KaScoreboard? = null

    // NMS 相关的反射缓存
    private val nmsVersion by lazy {
        Bukkit.getServer().javaClass.`package`.name.split(".")[3]
    }

    /**
     * 设置插件引用
     */
    fun init(kascoreboard: KaScoreboard) {
        plugin = kascoreboard
    }

    /**
     * 发送 ActionBar 消息（Spigot 兼容版本）
     * 使用 NMS 数据包发送，支持所有 Spigot 版本
     */
    private fun sendActionBar(player: Player, message: String) {
        try {
            // 获取 NMS 的 IChatBaseComponent 类
            val iChatBaseComponentClass = Class.forName("net.minecraft.server.$nmsVersion.IChatBaseComponent")

            // 将文本转换为 JSON 格式
            val jsonText = "{\"text\":\"$message\"}"

            // 反序列化为 IChatBaseComponent
            val component = try {
                // 尝试使用 IChatBaseComponent.ChatSerializer (新版本)
                val chatSerializerClass = Class.forName("net.minecraft.server.$nmsVersion.IChatBaseComponent\$ChatSerializer")
                chatSerializerClass.getDeclaredMethod("a", String::class.java)
                    .invoke(null, jsonText)
            } catch (e: Exception) {
                // 回退到 MinecraftServer.a (旧版本)
                try {
                    val minecraftServerClass = Class.forName("net.minecraft.server.$nmsVersion.MinecraftServer")
                    minecraftServerClass.getDeclaredMethod("a", String::class.java)
                        .invoke(null, jsonText)
                } catch (e2: Exception) {
                    // 回退到 ChatComponentText (最旧版本)
                    val chatComponentTextClass = Class.forName("net.minecraft.server.$nmsVersion.ChatComponentText")
                    chatComponentTextClass.getConstructor(String::class.java).newInstance(message)
                }
            }

            // 获取 PacketPlayOutTitle 类
            val packetPlayOutTitleClass = Class.forName("net.minecraft.server.$nmsVersion.PacketPlayOutTitle")

            // 获取 EnumTitleAction
            val enumTitleActionClass = Class.forName("net.minecraft.server.$nmsVersion.EnumTitleAction")

            // 获取 ActionBar 字段（新版本）或通过反射获取（旧版本）
            val actionBar = try {
                packetPlayOutTitleClass.getDeclaredField("ACTIONBAR").get(null)
            } catch (e: Exception) {
                // 尝试通过 values() 方法获取
                enumTitleActionClass.getDeclaredMethod("values").invoke(null)
                    .let { it as Array<*> }
                    .find { it?.toString()?.contains("actionbar", ignoreCase = true) == true }
            }

            // 创建 ActionBar 数据包
            val actionBarPacket = try {
                // 新版本构造器 (6个参数)
                packetPlayOutTitleClass.getDeclaredConstructor(
                    enumTitleActionClass,
                    iChatBaseComponentClass,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).newInstance(actionBar, component, 10, 70, 20)
            } catch (e: Exception) {
                // 旧版本构造器 (5个参数，没有淡入/淡出参数)
                try {
                    packetPlayOutTitleClass.getDeclaredConstructor(
                        enumTitleActionClass,
                        iChatBaseComponentClass
                    ).newInstance(actionBar, component)
                } catch (e2: Exception) {
                    // 最旧版本构造器 (3个参数)
                    packetPlayOutTitleClass.getDeclaredConstructor(
                        iChatBaseComponentClass,
                        iChatBaseComponentClass,
                        iChatBaseComponentClass
                    ).newInstance(null, component, null)
                }
            }

            // 获取 CraftPlayer 和 NMS Player
            val craftPlayerClass = Class.forName("org.bukkit.craftbukkit.$nmsVersion.entity.CraftPlayer")
            val craftPlayer = craftPlayerClass.cast(player)

            val getHandleMethod = craftPlayerClass.getDeclaredMethod("getHandle")
            val nmsPlayer = getHandleMethod.invoke(craftPlayer)

            // 获取 PlayerConnection (尝试不同的字段名)
            val playerConnectionClass = Class.forName("net.minecraft.server.$nmsVersion.PlayerConnection")
            val playerConnection = try {
                // 新版本: connection
                val connectionField = nmsPlayer.javaClass.getDeclaredField("connection")
                connectionField.isAccessible = true
                connectionField.get(nmsPlayer)
            } catch (e: Exception) {
                // 旧版本: b
                val bField = nmsPlayer.javaClass.getDeclaredField("b")
                bField.isAccessible = true
                bField.get(nmsPlayer)
            }

            // 发送数据包
            val packetClass = Class.forName("net.minecraft.server.$nmsVersion.Packet")
            val sendPacketMethod = playerConnectionClass.getDeclaredMethod("sendPacket", packetClass)
            sendPacketMethod.invoke(playerConnection, actionBarPacket)
        } catch (e: Exception) {
            plugin?.logger?.warning("发送 ActionBar 失败: ${e.message}")
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
                result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result)
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
                val sound = org.bukkit.Sound.valueOf(soundName.uppercase())
                player.playSound(player.location, sound, category, volume, pitch)
            } catch (e: IllegalArgumentException) {
                plugin?.logger?.warning("未知的声音: $soundName")
            }
        }
    }
}
