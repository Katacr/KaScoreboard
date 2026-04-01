package org.katacr.kaScoreboard

import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * 条件判断工具类
 * 支持复杂的逻辑表达式、PAPI 变量和多种比较运算符
 */
object ConditionUtils {

    /**
     * 解析并检查条件字符串 (支持 &&, || 复合条件)
     * 例如: "%player_is_op% == true && %player_level% >= 10"
     */
    fun checkCondition(player: Player, condition: String?): Boolean {
        if (condition == null || condition.isBlank()) {
            return true
        }

        var processed = condition

        // 1. 进行 PAPI 变量替换
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                processed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, processed)
            } catch (_: Exception) {
                // PAPI 解析失败，忽略
            }
        }

        // 2. 解析逻辑表达式（支持 && 和 ||）
        return parseLogicalExpression(player, processed)
    }

    /**
     * 获取条件值（支持条件判断的单值返回）
     * 格式:
     *   - condition: "%player_is_op% == true"
     *     allow: "管理员专属文本"
     *     deny: "普通玩家文本"
     *
     * @param player 玩家对象
     * @param conditionMap 条件映射，包含 condition、allow、deny 键
     * @return 条件满足时的 allow 值，否则返回 deny 值
     */
    fun getConditionalValue(
        player: Player,
        conditionMap: Map<*, *>,
        defaultValue: String = ""
    ): String {
        val condition = conditionMap["condition"] as? String ?: return defaultValue
        val allow = conditionMap["allow"] as? String ?: defaultValue
        val deny = conditionMap["deny"] as? String ?: defaultValue

        // 检查条件并返回相应的值
        return if (checkCondition(player, condition)) {
            allow
        } else {
            deny
        }
    }

    /**
     * 从列表中获取条件值（支持多个条件判断）
     * 遍历条件列表，返回第一个匹配条件的 meet 值，否则返回默认值
     *
     * @param player 玩家对象
     * @param conditions 条件列表
     * @param defaultValue 默认值
     * @return 第一个匹配条件的 meet 值，否则返回默认值
     */
    fun getConditionalValueFromList(
        player: Player,
        conditions: List<*>,
        defaultValue: String = ""
    ): String {
        for (condition in conditions) {
            if (condition is Map<*, *>) {
                val result = getConditionalValue(player, condition)
                if (result.isNotEmpty()) {
                    return result
                }
            }
        }
        return defaultValue
    }

    /**
     * 获取条件值（支持条件判断，返回字符串或字符串列表）
     * allow 和 deny 支持：
     *   - 字符串：直接返回
     *   - 列表：返回第一个元素（用于循环显示）
     *
     * @param player 玩家对象
     * @param conditionMap 条件映射，包含 condition、allow、deny 键
     * @return 条件满足时的 allow 值，否则返回 deny 值
     */
    fun getConditionalValueOrList(
        player: Player,
        conditionMap: Map<*, *>,
        defaultValue: String = ""
    ): String {
        // 优先尝试使用 getConditionalList（支持列表模式）
        val list = getConditionalList(player, conditionMap, emptyList())
        if (list.isNotEmpty()) {
            // 返回第一个元素（用于循环显示）
            return list[0]
        }

        // 回退到 getConditionalValue（支持字符串模式）
        return getConditionalValue(player, conditionMap, defaultValue)
    }

    /**
     * 从列表中获取条件值（支持多个条件判断，allow/deny 支持列表）
     * 遍历条件列表，返回第一个匹配条件的值，否则返回默认值
     *
     * @param player 玩家对象
     * @param conditions 条件列表
     * @param defaultValue 默认值
     * @return 第一个匹配条件的值，否则返回默认值
     */
    fun getConditionalValueOrListFromList(
        player: Player,
        conditions: List<*>,
        defaultValue: String = ""
    ): String {
        for (condition in conditions) {
            if (condition is Map<*, *>) {
                val result = getConditionalValueOrList(player, condition)
                if (result.isNotEmpty()) {
                    return result
                }
            }
        }
        return defaultValue
    }

    /**
     * 获取条件列表值（支持条件判断的列表返回）
     * 格式:
     *   - condition: "%player_is_op% == true"
     *     allow:
     *       - '管理员行1'
     *       - '管理员行2'
     *     deny:
     *       - '普通玩家行1'
     *       - '普通玩家行2'
     *
     * @param player 玩家对象
     * @param conditionMap 条件映射，包含 condition、allow、deny 键
     * @return 条件满足时的 allow 列表，否则返回 deny 列表
     */
    fun getConditionalList(
        player: Player,
        conditionMap: Map<*, *>,
        defaultValue: List<String> = emptyList()
    ): List<String> {
        val condition = conditionMap["condition"] as? String ?: return defaultValue
        val allow = (conditionMap["allow"] as? List<*>)?.filterIsInstance<String>() ?: defaultValue
        val deny = (conditionMap["deny"] as? List<*>)?.filterIsInstance<String>() ?: defaultValue

        // 检查条件并返回相应的列表
        return if (checkCondition(player, condition)) {
            allow
        } else {
            deny
        }
    }

    /**
     * 从列表中获取条件列表值（支持多个条件判断）
     * 遍历条件列表，返回第一个匹配条件的 allow 列表，否则返回默认列表
     *
     * @param player 玩家对象
     * @param conditions 条件列表
     * @param defaultValue 默认列表
     * @return 第一个匹配条件的 allow 列表，否则返回默认列表
     */
    fun getConditionalListFromList(
        player: Player,
        conditions: List<*>,
        defaultValue: List<String> = emptyList()
    ): List<String> {
        for (condition in conditions) {
            if (condition is Map<*, *>) {
                val result = getConditionalList(player, condition)
                if (result.isNotEmpty()) {
                    return result
                }
            }
        }
        return defaultValue
    }

    /**
     * 递归解析逻辑表达式（支持 && 和 ||）
     * 优先级：&& 高于 ||
     */
    private fun parseLogicalExpression(player: Player, expression: String): Boolean {
        val trimmed = expression.trim()

        // 1. 先检查是否包含 ||（优先级最低）
        val orParts = splitByOperator(trimmed, "||")
        if (orParts.size > 1) {
            // 如果有 ||，先解析第一部分，如果为 true 则直接返回 true（短路求值）
            val firstPart = orParts[0]
            val firstResult = parseLogicalExpression(player, firstPart)
            if (firstResult) return true // || 短路求值

            // 否则继续解析剩余部分
            val remaining = trimmed.substring(firstPart.length).trim().removePrefix("||").trim()
            return parseLogicalExpression(player, remaining)
        }

        // 2. 再检查是否包含 &&（优先级高于 ||）
        val andParts = splitByOperator(trimmed, "&&")
        if (andParts.size > 1) {
            // 如果有 &&，先解析第一部分，如果为 false 则直接返回 false（短路求值）
            val firstPart = andParts[0]
            val firstResult = parseLogicalExpression(player, firstPart)
            if (!firstResult) return false // && 短路求值

            // 否则继续解析剩余部分
            val remaining = trimmed.substring(firstPart.length).trim().removePrefix("&&").trim()
            return parseLogicalExpression(player, remaining)
        }

        // 3. 如果没有逻辑运算符，则为基本条件
        return evaluateSingleCondition(player, trimmed)
    }

    /**
     * 按运算符分割表达式（考虑括号、引号和优先级）
     */
    private fun splitByOperator(expression: String, operator: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0

        while (i < expression.length) {
            val char = expression[i]

            when {
                char == '\\' -> {
                    // 转义字符，跳过下一个字符
                    current.append(char)
                    i++
                    if (i < expression.length) {
                        current.append(expression[i])
                    }
                }
                char == '\'' && !inDoubleQuote -> {
                    inSingleQuote = !inSingleQuote
                    current.append(char)
                }
                char == '"' && !inSingleQuote -> {
                    inDoubleQuote = !inDoubleQuote
                    current.append(char)
                }
                char == '(' && !inSingleQuote && !inDoubleQuote -> {
                    parenDepth++
                    current.append(char)
                }
                char == ')' && !inSingleQuote && !inDoubleQuote -> {
                    parenDepth--
                    current.append(char)
                }
                parenDepth > 0 || inSingleQuote || inDoubleQuote -> {
                    // 括号内、引号内不分割
                    current.append(char)
                }
                else -> {
                    // 检查是否匹配运算符
                    if (i + operator.length <= expression.length &&
                        expression.substring(i, i + operator.length) == operator) {
                        // 匹配到运算符
                        result.add(current.toString().trim())
                        current = StringBuilder()
                        i += operator.length - 1 // 跳过运算符
                    } else {
                        current.append(char)
                    }
                }
            }
            i++
        }

        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }

        return result
    }

    /**
     * 评估单个条件（如 "5 >= 3" 或 "hasPerm.vip"）
     */
    private fun evaluateSingleCondition(player: Player, condition: String): Boolean {
        val trimmed = condition.trim()

        // 处理内置条件方法 method.value 格式
        // 检查是否包含 . 且不在引号内
        if (trimmed.contains(".") && !trimmed.matches(Regex("\".*\".*\\..*"))) {
            try {
                return evaluateBuiltinCondition(player, trimmed)
            } catch (e: NotBuiltinConditionException) {
                // 不是内置条件，继续普通比较
            }
        }

        // 如果是括号包裹的表达式，去掉括号后递归解析
        if (trimmed.startsWith("(") && trimmed.endsWith(")") && !trimmed.matches(Regex("\".*\".*\\(.*\\)"))) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            // 确保括号是成对的（考虑引号）
            var parenCount = 0
            var inSingleQuote = false
            var inDoubleQuote = false
            var isCompletePair = true
            for (char in inner) {
                when (char) {
                    '\\' -> continue // 跳过转义字符的下一个字符
                    '\'' if !inDoubleQuote -> inSingleQuote = !inSingleQuote
                    '"' if !inSingleQuote -> inDoubleQuote = !inDoubleQuote
                    '(' if !inSingleQuote && !inDoubleQuote -> parenCount++
                    ')' if !inSingleQuote && !inDoubleQuote -> parenCount--
                }
                if (parenCount < 0) {
                    isCompletePair = false
                    break
                }
            }
            if (isCompletePair && parenCount == 0 && !inSingleQuote && !inDoubleQuote) {
                return parseLogicalExpression(player, inner)
            }
        }

        // 匹配比较运算符
        val regex = "(>=|<=|==|!=|>|<)".toRegex()
        val match = regex.find(trimmed) ?: run {
            return false
        }

        val op = match.value
        val parts = trimmed.split(op, limit = 2)
        val left = parseQuotedString(parts[0].trim())
        val right = parseQuotedString(parts[1].trim())

        return when (op) {
            "==" -> compareEquals(left, right)
            "!=" -> !compareEquals(left, right)
            ">"  -> left.toDoubleDefault(0.0) > right.toDoubleDefault(0.0)
            ">=" -> left.toDoubleDefault(0.0) >= right.toDoubleDefault(0.0)
            "<"  -> left.toDoubleDefault(0.0) < right.toDoubleDefault(0.0)
            "<=" -> left.toDoubleDefault(0.0) <= right.toDoubleDefault(0.0)
            else -> false
        }
    }

    /**
     * 自定义异常：表示这不是一个内置条件，应该继续按普通比较处理
     */
    private class NotBuiltinConditionException : Exception()

    /**
     * 解析并执行内置条件方法
     * 格式: "method.value" 或 "!method.value" 或 "method.\"value\"" 或 "!method.\"value\""
     * 支持 ! 前缀进行反向判断
     * 支持的方法:
     *   - isNum: 判断是否为数字（整数或小数）
     *   - isPosNum: 判断是否为正数（大于0）
     *   - isInt: 判断是否为整数
     *   - isPosInt: 判断是否为正整数（大于0）
     *   - hasPerm: 判断玩家是否拥有权限 (value应为权限节点)
     * @throws NotBuiltinConditionException 如果这不是一个有效的内置条件格式
     */
    private fun evaluateBuiltinCondition(player: Player, condition: String): Boolean {
        val trimmed = condition.trim()

        // 检查是否为反向判断
        val isNegative = trimmed.startsWith("!")
        val conditionWithoutNegation = if (isNegative) trimmed.substring(1).trim() else trimmed

        // 查找第一个点号，但要考虑引号
        var dotIndex = -1
        var inQuote = false
        var i = 0
        while (i < conditionWithoutNegation.length) {
            val char = conditionWithoutNegation[i]
            when (char) {
                '\\' -> {
                    // 跳过转义字符的下一个字符
                    i++
                }
                '"' -> {
                    inQuote = !inQuote
                }
                '.' if !inQuote -> {
                    dotIndex = i
                    break
                }
            }
            i++
        }

        if (dotIndex == -1) {
            throw NotBuiltinConditionException()
        }

        val method = conditionWithoutNegation.take(dotIndex).trim()
        var value = conditionWithoutNegation.substring(dotIndex + 1).trim()

        // 如果值被双引号包裹，则去掉引号
        if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
            value = value.substring(1, value.length - 1)
        }

        val result = when (method) {
            "isNum" -> value.toDoubleOrNull() != null
            "isPosNum" -> value.toDoubleOrNull()?.let { it > 0 } ?: false
            "isInt" -> value.toIntOrNull() != null
            "isPosInt" -> value.toIntOrNull()?.let { it > 0 } ?: false
            "hasPerm" -> player.hasPermission(value)
            else -> {
                // 未知方法，说明这不是内置条件，应该按普通比较处理
                throw NotBuiltinConditionException()
            }
        }

        // 返回判断结果（如果为反向判断则取反）
        return if (isNegative) !result else result
    }

    private fun String.toDoubleDefault(default: Double): Double = this.toDoubleOrNull() ?: default

    /**
     * 解析引号包裹的字符串
     * 支持单引号和双引号包裹，如果未用引号包裹则直接返回原字符串
     * @param str 原始字符串
     * @return 去除引号后的字符串
     */
    private fun parseQuotedString(str: String): String {
        val trimmed = str.trim()

        // 检查是否被双引号包裹
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            // 检查引号是否匹配（避免像 "test 这样的情况）
            val content = trimmed.substring(1, trimmed.length - 1)
            // 检查内容中是否有未转义的引号
            if (!content.contains("\"")) {
                return content
            }
        }

        // 检查是否被单引号包裹
        if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length >= 2) {
            val content = trimmed.substring(1, trimmed.length - 1)
            if (!content.contains("'")) {
                return content
            }
        }

        // 未用引号包裹，直接返回
        return trimmed
    }

    /**
     * 比较两个值是否相等（支持数值和字符串）
     * 优先使用数值比较，失败则使用字符串比较
     */
    private fun compareEquals(left: String, right: String): Boolean {
        // 1. 先尝试数值比较
        val leftNum = left.toDoubleOrNull()
        val rightNum = right.toDoubleOrNull()

        if (leftNum != null && rightNum != null) {
            // 两边都是数字，使用数值比较
            return leftNum == rightNum
        }

        // 2. 尝试布尔值比较
        val leftBool = parseBoolean(left)
        val rightBool = parseBoolean(right)

        if (leftBool != null && rightBool != null) {
            return leftBool == rightBool
        }

        // 3. 字符串比较（忽略大小写）
        return left.equals(right, ignoreCase = true)
    }

    /**
     * 解析字符串为布尔值
     * 支持的 true 值：true, yes, 1, t, y
     * 支持的 false 值：false, no, 0, f, n
     * 其他情况返回 null
     */
    private fun parseBoolean(value: String): Boolean? {
        val normalized = value.trim().lowercase()
        return when (normalized) {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            else -> null
        }
    }
}
