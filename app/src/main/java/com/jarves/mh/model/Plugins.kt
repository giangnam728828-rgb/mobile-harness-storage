package com.jarves.mh.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** What a plugin tool does when the agent asks for it. Only shell commands are supported. */
enum class PluginToolKind {
    /** Runs a command inside the project's PRoot workspace. */
    COMMAND,
}

/** How dangerous the tool is; the approval dialog reuses the existing risk vocabulary. */
enum class PluginToolRisk {
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * One callable tool contributed by a plugin.
 *
 * [command] is a shell snippet executed through the same PRoot bridge as the project
 * terminal, so a plugin cannot reach anything the user could not already reach by hand.
 * Placeholders available to the command:
 *  - `{{workspace}}`  guest path of the project workspace
 *  - `{{args}}`       raw text the agent passed as arguments
 */
data class PluginTool(
    val id: String,
    val name: String,
    val description: String,
    val kind: PluginToolKind = PluginToolKind.COMMAND,
    val command: String,
    val risk: PluginToolRisk = PluginToolRisk.MEDIUM,
    val timeoutSeconds: Int = 120,
)

/**
 * A local plugin: a declarative bundle of tool definitions.
 *
 * Deliberately data-only. Nothing here is executed or imported as code, so installing a
 * plugin cannot run arbitrary logic outside the commands the user can read in the file.
 */
data class PluginManifest(
    val id: String,
    val displayName: String,
    val version: String = "1.0.0",
    val description: String = "",
    val author: String = "",
    val enabled: Boolean = true,
    val tools: List<PluginTool> = emptyList(),
    val source: PluginSource = PluginSource.USER,
) {
    val toolCount: Int get() = tools.size

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("version", version)
        put("description", description)
        put("author", author)
        put("enabled", enabled)
        put("tools", JSONArray().apply {
            tools.forEach { tool ->
                put(JSONObject().apply {
                    put("id", tool.id)
                    put("name", tool.name)
                    put("description", tool.description)
                    put("kind", tool.kind.name)
                    put("command", tool.command)
                    put("risk", tool.risk.name)
                    put("timeoutSeconds", tool.timeoutSeconds)
                })
            }
        })
    }

    companion object {
        private const val MAX_TOOLS = 32
        private const val MAX_ID_LENGTH = 64

        /**
         * Parses a manifest, returning null when the JSON is unusable.
         *
         * Parsing is strict about ids and tool entries because a malformed plugin should
         * disappear from the list rather than half-load and confuse the agent.
         */
        fun fromJson(raw: String): PluginManifest? = runCatching {
            val obj = JSONObject(raw)
            val id = obj.optString("id").trim()
            // require() rather than `return null`: the latter would be a non-local return
            // out of fromJson and skip the runCatching wrapper entirely.
            require(id.isNotEmpty() && id.length <= MAX_ID_LENGTH && id.matches(Regex("[a-z0-9][a-z0-9._-]*"))) {
                "bad plugin id"
            }
            val tools = mutableListOf<PluginTool>()
            val arr = obj.optJSONArray("tools") ?: JSONArray()
            for (index in 0 until minOf(arr.length(), MAX_TOOLS)) {
                val t = arr.optJSONObject(index) ?: continue
                val toolId = t.optString("id").trim()
                val command = t.optString("command").trim()
                if (toolId.isBlank() || command.isBlank()) continue
                tools.add(
                    PluginTool(
                        id = toolId,
                        name = t.optString("name").ifBlank { toolId },
                        description = t.optString("description"),
                        kind = runCatching { PluginToolKind.valueOf(t.optString("kind", "COMMAND")) }
                            .getOrDefault(PluginToolKind.COMMAND),
                        command = command,
                        risk = runCatching { PluginToolRisk.valueOf(t.optString("risk", "MEDIUM")) }
                            .getOrDefault(PluginToolRisk.MEDIUM),
                        timeoutSeconds = t.optInt("timeoutSeconds", 120).coerceIn(5, 900),
                    ),
                )
            }
            PluginManifest(
                id = id,
                displayName = obj.optString("displayName").ifBlank { id },
                version = obj.optString("version").ifBlank { "1.0.0" },
                description = obj.optString("description"),
                author = obj.optString("author"),
                enabled = obj.optBoolean("enabled", true),
                tools = tools,
            )
        }.getOrNull()

        /** A starter manifest offered in the settings screen so the format is self-documenting. */
        fun sampleTemplate(): String = JSONObject().apply {
            put("id", "my-first-plugin")
            put("displayName", "我的第一个插件")
            put("version", "1.0.0")
            put("description", "示例：查看项目文件、跑一次 git status")
            put("author", "")
            put("enabled", true)
            put(
                "tools",
                JSONArray()
                    .put(
                        JSONObject().apply {
                            put("id", "list-files")
                            put("name", "列出项目文件")
                            put("description", "显示当前项目里的文件，最多 50 行")
                            put("kind", "COMMAND")
                            put("command", "ls -la {{workspace}} | head -50")
                            put("risk", "LOW")
                            put("timeoutSeconds", 30)
                        },
                    )
                    .put(
                        JSONObject().apply {
                            put("id", "git-status")
                            put("name", "Git 状态")
                            put("description", "查看当前项目有哪些改动")
                            put("kind", "COMMAND")
                            put("command", "cd {{workspace}} && git status")
                            put("risk", "LOW")
                            put("timeoutSeconds", 60)
                        },
                    ),
            )
        }.toString(2)

        /**
         * Replaces `{{workspace}}` and `{{args}}` in a tool command.
         *
         * Substitution is deliberately plain text: no evaluation, no shell-quoting tricks,
         * so a manifest author can read exactly what will run.
         */
        fun renderCommand(command: String, workspacePath: String, args: String): String =
            command.replace("{{workspace}}", workspacePath).replace("{{args}}", args)

        fun newId(): String = "plugin-" + UUID.randomUUID().toString().take(8)
    }
}

/** Where a manifest came from, so the UI can tell bundled examples from the user's own. */
enum class PluginSource {
    USER,
    SAMPLE,
}
