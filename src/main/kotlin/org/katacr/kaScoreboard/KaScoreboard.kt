package org.katacr.kaScoreboard

import net.byteflux.libby.BukkitLibraryManager
import net.byteflux.libby.Library
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaScoreboard.menu.MenuHandler
import org.katacr.kaScoreboard.menu.MenuListener
import org.katacr.kaScoreboard.menu.MenuManager
import java.io.File

class KaScoreboard : JavaPlugin() {

    lateinit var databaseManager: DatabaseManager
    lateinit var scoreboardManager: ScoreboardManager
    lateinit var scoreboardHandler: ScoreboardHandler
    lateinit var menuManager: MenuManager
    lateinit var menuHandler: MenuHandler

    override fun onLoad() {
        // 创建共享的库目录（服务器根目录下的libraries文件夹）
        val librariesDir = File(dataFolder.parentFile.parentFile, "libraries")
        if (!librariesDir.exists()) {
            librariesDir.mkdirs()
        }

        val libraryManager = BukkitLibraryManager(this, librariesDir.absolutePath)

        // 添加 Maven 中央仓库和阿里云镜像（加速国内下载）
        libraryManager.addMavenCentral()
        libraryManager.addRepository("https://maven.aliyun.com/repository/public")

        // Kotlin 标准库
        val kotlinStd = Library.builder()
            .groupId("org{}jetbrains{}kotlin")
            .artifactId("kotlin-stdlib")
            .version("1.9.22")
            .build()

        // MySQL Connector/J 驱动
        val mysql = Library.builder()
            .groupId("com{}mysql")
            .artifactId("mysql-connector-j")
            .version("9.1.0")
            .build()

        // HikariCP 连接池
        val hikari = Library.builder()
            .groupId("com{}zaxxer")
            .artifactId("HikariCP")
            .version("5.1.0")
            .build()

        // SQLite JDBC 驱动
        val sqlite = Library.builder()
            .groupId("org{}xerial")
            .artifactId("sqlite-jdbc")
            .version("3.46.1.0")
            .build()


        logger.info("Checking and downloading necessary dependent libraries, please wait...")

        libraryManager.loadLibrary(kotlinStd)
        libraryManager.loadLibrary(sqlite)
        libraryManager.loadLibrary(mysql)
        libraryManager.loadLibrary(hikari)
    }

    override fun onEnable() {
        // 保存默认配置
        saveDefaultConfig()

        // 初始化数据库管理器
        databaseManager = DatabaseManager(this)
        databaseManager.setup()

        // 初始化计分板管理器
        scoreboardManager = ScoreboardManager(this)
        scoreboardManager.loadScoreboards()

        // 初始化计分板处理器
        scoreboardHandler = ScoreboardHandler(this)

        // 初始化菜单管理器
        menuManager = MenuManager()

        // 初始化菜单处理器
        menuHandler = MenuHandler(this)
        menuHandler.loadMenus()

        // 注册监听器
        server.pluginManager.registerEvents(ScoreboardListener(this), this)
        server.pluginManager.registerEvents(MenuListener(this), this)

        // 注册命令
        val scoreboardCommand = ScoreboardCommand(this)
        getCommand("kascoreboard")?.let { cmd ->
            cmd.setExecutor(scoreboardCommand)
            cmd.tabCompleter = scoreboardCommand
        }

        // 启动自动更新任务
        scoreboardHandler.startUpdateTask()
        menuHandler.startUpdateTask()

        logger.info("Plugin enabled successfully!")
    }

    override fun onDisable() {
        // 清除所有计分板和菜单
        if (::scoreboardHandler.isInitialized) {
            scoreboardHandler.clearAll()
        }
        if (::menuHandler.isInitialized) {
            menuHandler.clearAll()
        }

        // 关闭数据库连接
        if (::databaseManager.isInitialized) {
            databaseManager.close()
        }

        // 清除计分板管理器
        if (::scoreboardManager.isInitialized) {
            scoreboardManager.clear()
        }

        logger.info("Plugin disabled successfully!")
    }
}
