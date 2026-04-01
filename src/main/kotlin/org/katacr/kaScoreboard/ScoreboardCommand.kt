package org.katacr.kaScoreboard

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * 计分板命令处理器
 */
class ScoreboardCommand(private val plugin: KaScoreboard) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c只有玩家可以使用此命令!"))
            return true
        }

        if (args.isEmpty()) {
            // 无参数：切换显示/隐藏
            val showing = plugin.scoreboardHandler.toggleScoreboard(sender)
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                if (showing) "&a计分板已开启" else "&e计分板已关闭"))
            return true
        }

        when (args[0].lowercase()) {
            "show" -> {
                if (args.size < 2) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&c用法: /ksb show <计分板名称>"))
                    return true
                }
                val scoreboardName = args[1]
                plugin.scoreboardHandler.showScoreboard(sender, scoreboardName)
            }

            "on" -> {
                if (plugin.scoreboardHandler.hasScoreboard(sender)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c你的计分板已经是开启状态!"))
                    return true
                }
                // 显示上次使用的计分板或默认计分板
                val lastScoreboard = plugin.databaseManager.getPlayerScoreboard(sender.uniqueId)
                if (lastScoreboard != null) {
                    plugin.scoreboardHandler.showScoreboard(sender, lastScoreboard)
                } else {
                    plugin.scoreboardHandler.showScoreboard(sender, "default")
                }
            }

            "off" -> {
                if (!plugin.scoreboardHandler.hasScoreboard(sender)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c你的计分板已经是关闭状态!"))
                    return true
                }
                plugin.scoreboardHandler.hideScoreboard(sender)
            }

            "list" -> {
                val scoreboards = plugin.scoreboardManager.getAllScoreboardIds()
                if (scoreboards.isEmpty()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&c没有可用的计分板!"))
                    return true
                }
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&6===== 可用的计分板 ====="))
                scoreboards.forEach { id ->
                    val config = plugin.scoreboardManager.getScoreboardConfig(id)
                    val name = config?.getString("name") ?: id
                    val description = config?.getString("description") ?: ""
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&e- $id &7($name)"))
                    if (description.isNotEmpty()) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "  &7$description"))
                    }
                }
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&6========================="))
            }

            "reload" -> {
                if (!sender.hasPermission("kascoreboard.reload")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&c你没有权限使用此命令!"))
                    return true
                }
                val scoreboardCount = plugin.scoreboardManager.reload()
                val menuCount = plugin.menuHandler.reload()
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&a已重载 $scoreboardCount 个计分板配置, $menuCount 个菜单配置"))
            }

            "menu" -> {
                if (args.size < 2) {
                    // 无参数：列出所有菜单
                    val menus = plugin.menuHandler.getAllMenuIds()
                    if (menus.isEmpty()) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&c没有可用的菜单!"))
                        return true
                    }
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&6===== 可用的菜单 ====="))
                    menus.forEach { id ->
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&e- $id"))
                    }
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&6========================"))
                    return true
                }
                when (args[1].lowercase()) {
                    "open" -> {
                        if (args.size < 3) {
                            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                "&c用法: /ksb menu open <菜单ID>"))
                            return true
                        }
                        plugin.menuHandler.openMenu(sender, args[2])
                    }
                    "close" -> {
                        plugin.menuHandler.closeMenu(sender)
                    }
                    else -> {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&c未知命令! 使用 /ksb menu help 查看帮助"))
                    }
                }
            }

            "help" -> {
                sendHelp(sender)
            }

            else -> {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&c未知命令! 使用 /ksb help 查看帮助"))
            }
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player) return emptyList()

        if (args.isEmpty()) {
            return listOf("show", "on", "off", "list", "reload", "menu", "help")
        }

        if (args.size == 1) {
            return listOf("show", "on", "off", "list", "reload", "menu", "help")
                .filter { it.lowercase().startsWith(args[0].lowercase()) }
        }

        if (args.size == 2 && args[0].lowercase() == "show") {
            val input = args[1].lowercase()
            return plugin.scoreboardManager.getAllScoreboardIds()
                .filter { it.lowercase().startsWith(input) }
        }

        if (args.size == 2 && args[0].lowercase() == "menu") {
            return listOf("open", "close", "help")
                .filter { it.lowercase().startsWith(args[1].lowercase()) }
        }

        if (args.size == 3 && args[0].lowercase() == "menu" && args[1].lowercase() == "open") {
            val input = args[2].lowercase()
            return plugin.menuHandler.getAllMenuIds()
                .filter { it.lowercase().startsWith(input) }
        }

        return emptyList()
    }

    /**
     * 发送帮助信息
     */
    private fun sendHelp(player: Player) {
        val scoreboardName = plugin.scoreboardHandler.getPlayerScoreboard(player) ?: "&c无"

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            """
                &6===== KaScoreboard 帮助 =====
                &e当前计分板: $scoreboardName
                &6=========================
                &e/ksb &7- 切换计分板显示/隐藏
                &e/ksb show <计分板> &7- 显示指定计分板
                &e/ksb on &7- 开启计分板
                &e/ksb off &7- 关闭计分板
                &e/ksb list &7- 列出所有计分板
                &e/ksb reload &7- 重载计分板配置
                &e/ksb menu &7- 菜单管理
                &e/ksb menu open <菜单ID> &7- 打开指定菜单
                &e/ksb menu close &7- 关闭当前菜单
                &e/ksb help &7- 显示此帮助信息
                &6=========================
            """.trimIndent()))
    }
}
