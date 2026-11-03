package com.jarves.mh.model

import org.json.JSONObject

/** What an agent may ask the phone to do. Everything here moves or types on screen. */
enum class ScreenActionKind {
    TAP,
    SWIPE,
    BACK,
    HOME,
    RECENTS,
    TYPE,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    WAIT,
}

/** Outcome of one requested action, written back so the agent can branch on it. */
enum class ScreenActionStatus {
    OK,
    DENIED,
    BLOCKED,
    FAILED,
    TIMEOUT,
}

/**
 * A single action an agent asked for.
 *
 * Requests arrive from the guest Linux runtime as JSON files, never as direct calls, so
 * the host stays in charge: nothing runs until the user approves it in the app.
 */
data class ScreenActionRequest(
    val id: String,
    val kind: ScreenActionKind,
    val x: Int = 0,
    val y: Int = 0,
    val x2: Int = 0,
    val y2: Int = 0,
    val durationMs: Int = 220,
    val text: String = "",
    val reason: String = "",
) {
    /** One-line summary shown in the approval dialog. */
    fun describe(): String = when (kind) {
        ScreenActionKind.TAP -> "点击 ($x, $y)"
        ScreenActionKind.SWIPE -> "滑动 ($x,$y) → ($x2,$y2)"
        ScreenActionKind.BACK -> "返回"
        ScreenActionKind.HOME -> "回到桌面"
        ScreenActionKind.RECENTS -> "打开最近任务"
        ScreenActionKind.TYPE -> "输入文字：$text"
        ScreenActionKind.SCROLL_FORWARD -> "向下滚动"
        ScreenActionKind.SCROLL_BACKWARD -> "向上滚动"
        ScreenActionKind.WAIT -> "等待 ${durationMs} 毫秒"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("x", x)
        put("y", y)
        put("x2", x2)
        put("y2", y2)
        put("durationMs", durationMs)
        put("text", text)
        put("reason", reason)
    }

    companion object {
        private const val MAX_TEXT = 2_000

        /** Returns null for anything unparseable; a bad request must never be half-run. */
        fun fromJson(raw: String): ScreenActionRequest? = runCatching {
            val obj = JSONObject(raw)
            val id = obj.optString("id").trim()
            require(id.isNotEmpty()) { "missing id" }
            val kind = ScreenActionKind.valueOf(obj.optString("kind").trim().uppercase())
            ScreenActionRequest(
                id = id,
                kind = kind,
                x = obj.optInt("x", 0),
                y = obj.optInt("y", 0),
                x2 = obj.optInt("x2", 0),
                y2 = obj.optInt("y2", 0),
                durationMs = obj.optInt("durationMs", 220).coerceIn(20, 5_000),
                text = obj.optString("text").take(MAX_TEXT),
                reason = obj.optString("reason").take(300),
            )
        }.getOrNull()
    }
}

/** Result of executing (or refusing) a [ScreenActionRequest]. */
data class ScreenActionResult(
    val id: String,
    val status: ScreenActionStatus,
    val detail: String = "",
    /** Screen text right after the action, so the agent can see what changed. */
    val snapshot: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("status", status.name)
        put("detail", detail)
        put("snapshot", snapshot)
    }
}

/** Result of a one-off text input helper, kept separate so the blocklist check is obvious. */
data class ScreenTextOutcome(val ok: Boolean, val detail: String)
