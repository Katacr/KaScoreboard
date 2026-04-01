package org.katacr.kaScoreboard

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * 计分板管理器
 * 用于管理所有计分板配置文件的加载和释放
 */
class ScoreboardManager(private val plugin: KaScoreboard) {
    private val scoreboards = mutableMapOf<String, YamlConfiguration>()

    /**
     * 加载所有计分板
     */
    fun loadScoreboards() {
        val folder = File(plugin.dataFolder, "scoreboards")
        if (!folder.exists()) folder.mkdirs()

        // 首次加载时释放所有默认计分板
        if (folder.listFiles()?.isEmpty() == true) {
            saveDefaultScoreboards(folder, "scoreboards")
            plugin.logger.info("已释放默认计分板配置文件")
        }

        // 递归加载所有计分板文件
        loadScoreboardsRecursively(folder, "")
        plugin.logger.info("已加载 ${scoreboards.size} 个计分板配置")
    }

    /**
     * 从 jar 包内递归释放所有默认计分板文件和文件夹到服务器
     * @param folder 目标文件夹
     * @param resourcePath jar 包内的资源路径前缀
     */
    private fun saveDefaultScoreboards(folder: File, resourcePath: String) {
        try {
            // 尝试从 jar 包中获取资源
            val url = plugin.javaClass.classLoader.getResource(resourcePath)

            if (url == null) {
                plugin.logger.warning("找不到资源路径: $resourcePath")
                return
            }

            when (url.protocol) {
                "file" -> {
                    // IDE 开发环境，直接从文件系统读取
                    val sourceDir = File(url.toURI())
                    saveDefaultScoreboardsFromFileSystem(folder, sourceDir, resourcePath)
                }
                "jar" -> {
                    // 生产环境，从 jar 包读取
                    val jarPath = url.path.substringBefore("!")
                    val jarFile = File(jarPath.substringAfter("file:"))
                    saveDefaultScoreboardsFromJar(folder, jarFile, resourcePath)
                }
                else -> {
                    plugin.logger.warning("不支持的协议: ${url.protocol}")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("释放默认计分板时出错: ${e.message}")
        }
    }

    /**
     * 从文件系统（IDE环境）复制计分板文件
     */
    private fun saveDefaultScoreboardsFromFileSystem(targetFolder: File, sourceDir: File, resourcePath: String) {
        sourceDir.listFiles()?.forEach { file ->
            val targetFile = File(targetFolder, file.name)

            if (file.isDirectory) {
                // 递归创建子文件夹
                if (!targetFile.exists()) {
                    targetFile.mkdirs()
                }
                saveDefaultScoreboardsFromFileSystem(targetFile, file, "$resourcePath/${file.name}")
            } else if (file.name.endsWith(".yml")) {
                // 复制 yml 文件
                if (!targetFile.exists()) {
                    file.copyTo(targetFile, overwrite = false)
                }
            }
        }
    }

    /**
     * 从 jar 包中提取计分板文件
     */
    private fun saveDefaultScoreboardsFromJar(targetFolder: File, jarFile: File, resourcePath: String) {
        try {
            java.util.zip.ZipFile(jarFile).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith("$resourcePath/") }
                    .filter { !it.isDirectory }
                    .filter { it.name.endsWith(".yml") }
                    .forEach { entry ->
                        val relativePath = entry.name.substringAfter("$resourcePath/")
                        val targetFile = File(targetFolder, relativePath)

                        // 确保父文件夹存在
                        targetFile.parentFile?.mkdirs()

                        // 提取文件
                        if (!targetFile.exists()) {
                            zip.getInputStream(entry).use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            plugin.logger.warning("从jar包提取计分板时出错: ${e.message}")
        }
    }

    /**
     * 递归加载文件夹中的所有计分板文件
     * @param folder 要扫描的文件夹
     * @param prefix 计分板 ID 前缀（用于子文件夹）
     */
    private fun loadScoreboardsRecursively(folder: File, prefix: String) {
        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                // 递归处理子文件夹
                val newPrefix = if (prefix.isEmpty()) file.name else "$prefix/${file.name}"
                loadScoreboardsRecursively(file, newPrefix)
            } else if (file.extension == "yml") {
                // 加载计分板文件
                val scoreboardId = if (prefix.isEmpty()) file.nameWithoutExtension else "$prefix/${file.nameWithoutExtension}"
                val config = YamlConfiguration.loadConfiguration(file)
                scoreboards[scoreboardId] = config
                plugin.logger.info("已加载计分板: $scoreboardId")
            }
        }
    }

    /**
     * 获取计分板配置
     */
    fun getScoreboardConfig(id: String): YamlConfiguration? {
        return scoreboards[id]
    }

    /**
     * 获取所有计分板ID
     */
    fun getAllScoreboardIds(): List<String> {
        return scoreboards.keys.toList()
    }

    /**
     * 重载所有计分板
     */
    fun reload(): Int {
        scoreboards.clear()
        loadScoreboards()
        return getAllScoreboardIds().size
    }

    /**
     * 清除所有计分板
     */
    fun clear() {
        scoreboards.clear()
    }
}
