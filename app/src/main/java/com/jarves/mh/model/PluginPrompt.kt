package com.jarves.mh.model

/**
 * Renders enabled plugin tools into a prompt section the coding agent can read.
 *
 * The app cannot register native tools inside the third-party agent CLIs, so a plugin
 * tool is presented as an instructed command the agent may run through its normal
 * terminal tool. That keeps every execution inside the existing approval flow — the
 * agent still has to request the command, and the user still sees and approves it.
 */
object PluginPrompt {

    /** Risk labels are shown verbatim so the agent can weigh how carefully to proceed. */
    private fun PluginToolRisk.label(): String = when (this) {
        PluginToolRisk.LOW -> "safe to run without asking twice"
        PluginToolRisk.MEDIUM -> "ask the user before running it"
        PluginToolRisk.HIGH -> "always confirm with the user first; it can change or delete data"
    }

    /**
     * Returns an empty string when there are no enabled tools, so callers can append
     * unconditionally without leaving empty sections in the prompt.
     */
    fun section(plugins: List<PluginManifest>, guestWorkspacePath: String): String {
        val tools = plugins
            .filter { it.enabled }
            .flatMap { plugin -> plugin.tools.map { plugin to it } }
            .filter { (_, tool) -> tool.command.isNotBlank() }
        if (tools.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("<plugins>")
        sb.appendLine("The user installed these local tools. Each one is a shell command; run it with your normal terminal tool when it helps the request.")
        sb.appendLine("Never invent a plugin tool that is not listed here, and never run one the user disabled.")
        for ((plugin, tool) in tools) {
            val rendered = PluginManifest.renderCommand(tool.command, guestWorkspacePath, "")
            sb.appendLine()
            sb.appendLine("- ${tool.name} (plugin: ${plugin.displayName}, id: ${plugin.id}/${tool.id})")
            if (tool.description.isNotBlank()) sb.appendLine("  what it does: ${tool.description}")
            sb.appendLine("  run: $rendered")
            sb.appendLine("  risk: ${tool.risk.name.lowercase()} — ${tool.risk.label()}")
        }
        sb.appendLine("</plugins>")
        return sb.toString()
    }
}
