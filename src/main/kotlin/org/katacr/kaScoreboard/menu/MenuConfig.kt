package org.katacr.kaScoreboard.menu

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/**
 * 菜单按钮配置
 */
data class MenuButtonConfig(
    val id: String,                      // 按钮ID
    val updateInterval: Long,            // 更新间隔（tick）
    val unselectedText: List<String>,    // 未选中状态文本列表（已解析条件）
    val selectedText: List<String>,      // 选中状态文本列表（已解析条件）
    val unselectedConditions: List<Map<*, *>>,  // 未选中状态条件判断列表
    val selectedConditions: List<Map<*, *>>     // 选中状态条件判断列表
) {
    /**
     * 根据选中状态获取文本列表
     */
    fun getTextList(selected: Boolean): List<String> {
        return if (selected) selectedText else unselectedText
    }

    /**
     * 根据选中状态获取条件列表
     */
    fun getConditions(selected: Boolean): List<Map<*, *>> {
        return if (selected) selectedConditions else unselectedConditions
    }

    /**
     * 获取更新文本
     */
    fun getText(selected: Boolean, index: Int = 0): String {
        val textList = getTextList(selected)
        if (textList.isEmpty()) return ""
        return textList[index % textList.size]
    }
}

/**
 * 菜单滚动模式
 */
enum class ScrollMode {
    /** 循环滚动 */
    LOOP,
    /** 边界停止 */
    CLAMP
}

/**
 * 菜单配置
 */
data class MenuConfig(
    val id: String,                      // 菜单ID（文件名）
    val name: String,                    // 菜单名称
    val description: String,             // 菜单描述
    val scrollMode: ScrollMode,          // 滚动模式
    val titleConfig: MenuLineConfig,     // 标题配置
    val headerConfigs: List<MenuLineConfig>,  // 头部配置列表
    val footerConfigs: List<MenuLineConfig>,  // 底部配置列表
    val buttons: List<MenuButtonConfig>, // 按钮配置列表
    val rawConfig: YamlConfiguration     // 原始配置（用于条件判断等）
) {
    /**
     * 获取最大可显示的按钮数量
     */
    fun getMaxVisibleButtons(): Int {
        return 15 - headerConfigs.size - footerConfigs.size
    }
}

/**
 * 菜单行配置（用于header/footer/title）
 */
data class MenuLineConfig(
    val updateInterval: Long,            // 更新间隔（tick）
    val texts: List<String>,             // 文本列表
    val conditions: List<Map<*, *>>      // 条件判断列表
) {
    /**
     * 获取当前文本
     */
    fun getText(index: Int = 0): String {
        if (texts.isEmpty()) return ""
        return texts[index % texts.size]
    }
}

/**
 * 菜单配置解析器
 */
object MenuConfigParser {

    /**
     * 从YAML配置解析菜单配置
     */
    fun parse(menuId: String, config: YamlConfiguration): MenuConfig? {
        val name = config.getString("name", "未命名菜单") ?: return null
        val description = config.getString("description", "") ?: ""

        // 解析滚动模式
        val scrollModeStr = config.getString("scroll-mode", "clamp") ?: "clamp"
        val scrollMode = when (scrollModeStr.lowercase()) {
            "loop" -> ScrollMode.LOOP
            else -> ScrollMode.CLAMP
        }

        // 解析标题
        val titleConfig = parseLineConfig(config, "title") ?: MenuLineConfig(5, listOf("&a菜单"), emptyList())

        // 解析头部
        val headerSection = config.getConfigurationSection("header")
        val headerConfigs = if (headerSection != null) {
            headerSection.getKeys(false).mapNotNull { key ->
                parseLineConfig(config, "header.$key")
            }
        } else {
            emptyList()
        }

        // 解析底部
        val footerSection = config.getConfigurationSection("footer")
        val footerConfigs = if (footerSection != null) {
            footerSection.getKeys(false).mapNotNull { key ->
                parseLineConfig(config, "footer.$key")
            }
        } else {
            emptyList()
        }

        // 解析按钮
        val buttonSection = config.getConfigurationSection("buttons")
        val buttons = if (buttonSection != null) {
            buttonSection.getKeys(false).mapNotNull { buttonId ->
                parseButtonConfig(config, "buttons.$buttonId", buttonId)
            }
        } else {
            emptyList()
        }

        // 检查配置有效性
        val maxVisibleButtons = 15 - headerConfigs.size - footerConfigs.size
        if (maxVisibleButtons <= 0) {
            return null  // header + footer 超过15行，无法显示按钮
        }

        return MenuConfig(
            id = menuId,
            name = name,
            description = description,
            scrollMode = scrollMode,
            titleConfig = titleConfig,
            headerConfigs = headerConfigs,
            footerConfigs = footerConfigs,
            buttons = buttons,
            rawConfig = config
        )
    }

    /**
     * 解析行配置
     */
    private fun parseLineConfig(config: YamlConfiguration, path: String): MenuLineConfig? {
        val updateInterval = config.getLong("$path.update", -1)
        val textSection = config.getConfigurationSection("$path.text")
        val textList = config.getList("$path.text")

        // 处理不同的文本格式
        val (texts, conditions) = when {
            textSection != null -> {
                // 如果text是一个配置节，检查是否有condition键
                if (textSection.contains("condition")) {
                    // 条件判断格式
                    val conditionMap = mutableMapOf<String, Any>()
                    textSection.getKeys(true).forEach { key ->
                        textSection.get(key)?.let { conditionMap[key] = it }
                    }
                    emptyList<String>() to listOf(conditionMap)
                } else {
                    // 配置节但没有condition，可能是列表格式
                    val list = textSection.getList("") ?: emptyList<Any>()
                    val firstItem = list.firstOrNull()
                    if (firstItem is Map<*, *>) {
                        // 条件判断列表格式
                        emptyList<String>() to list.filterIsInstance<Map<*, *>>()
                    } else {
                        // 简单字符串列表格式
                        list.mapNotNull { it as? String } to emptyList()
                    }
                }
            }
            textList != null -> {
                // text是一个列表
                val firstItem = textList.firstOrNull()
                if (firstItem is Map<*, *>) {
                    // 条件判断列表格式
                    emptyList<String>() to textList.filterIsInstance<Map<*, *>>()
                } else {
                    // 简单字符串列表格式
                    textList.mapNotNull { it as? String } to emptyList()
                }
            }
            else -> {
                // 尝试直接获取字符串
                val text = config.getString("$path.text", "") ?: ""
                if (text.isNotEmpty()) {
                    listOf(text) to emptyList()
                } else {
                    emptyList<String>() to emptyList()
                }
            }
        }

        if (conditions.isEmpty() && texts.isEmpty()) return null

        return MenuLineConfig(updateInterval, texts, conditions)
    }

    /**
     * 解析按钮配置
     */
    private fun parseButtonConfig(config: YamlConfiguration, path: String, buttonId: String): MenuButtonConfig? {
        val updateInterval = config.getLong("$path.update", 10)
        val textSection = config.getConfigurationSection("$path.text") ?: return null

        // 解析未选中状态文本和条件
        val (unselectedTexts, unselectedConditions) = parseButtonTextSection(textSection, "un-selected")

        // 解析选中状态文本和条件
        val (selectedTexts, selectedConditions) = parseButtonTextSection(textSection, "selected")

        if (unselectedTexts.isEmpty() && selectedTexts.isEmpty() && unselectedConditions.isEmpty() && selectedConditions.isEmpty()) {
            return null
        }

        return MenuButtonConfig(
            id = buttonId,
            updateInterval = updateInterval,
            unselectedText = unselectedTexts,
            selectedText = selectedTexts,
            unselectedConditions = unselectedConditions,
            selectedConditions = selectedConditions
        )
    }

    /**
     * 解析按钮的文本节（un-selected 或 selected）
     * 支持格式：
     * 1. 条件判断列表格式：[ {condition:..., allow:[...], deny:[...]} ]
     * 2. 简单字符串格式：'文本内容'
     * 3. 字符串列表格式：['文本1', '文本2']
     */
    private fun parseButtonTextSection(textSection: ConfigurationSection, key: String): Pair<List<String>, List<Map<*, *>>> {
        // 尝试获取为列表
        val listValue = textSection.getList(key)
        if (listValue != null) {
            val firstItem = listValue.firstOrNull()
            if (firstItem is Map<*, *>) {
                // 检查是否是条件判断格式
                if (firstItem.containsKey("condition") || firstItem.containsKey("allow") || firstItem.containsKey("deny")) {
                    // 条件判断列表格式
                    val conditions = mutableListOf<Map<String, Any>>()
                    listValue.filterIsInstance<Map<*, *>>().forEach { item ->
                        val conditionMap = mutableMapOf<String, Any>()
                        item.keys.forEach { k ->
                            item[k]?.let { v -> conditionMap[k.toString()] = v }
                        }
                        if (conditionMap.isNotEmpty()) {
                            conditions.add(conditionMap)
                        }
                    }
                    return emptyList<String>() to conditions
                }
            }

            // 简单字符串列表格式
            return listValue.mapNotNull { it as? String }.filter { it.isNotEmpty() } to emptyList()
        }

        // 先尝试获取为字符串
        val stringValue = textSection.getString(key)
        if (stringValue != null && stringValue.isNotEmpty()) {
            // 简单字符串格式
            return listOf(stringValue) to emptyList()
        }

        return emptyList<String>() to emptyList()
    }

    /**
     * 从配置节解析文本列表和条件
     */
    private fun parseTextList(section: ConfigurationSection, defaultValue: String): List<String> {
        // 检查是否为条件判断格式
        val keys = section.getKeys(false)
        if (keys.contains("condition")) {
            // 条件判断格式
            return emptyList()  // 实际文本从条件判断中获取
        }

        // 简单字符串格式
        return keys.mapNotNull { key ->
            section.getString(key, defaultValue)
        }
    }

}
