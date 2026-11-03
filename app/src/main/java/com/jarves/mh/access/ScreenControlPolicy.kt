package com.jarves.mh.access

import com.jarves.mh.model.ScreenActionKind
import com.jarves.mh.model.ScreenActionRequest
import com.jarves.mh.model.ScreenActionStatus
import com.jarves.mh.model.ScreenActionResult

/**
 * Decides whether an agent's requested action may even be offered to the user.
 *
 * The approval dialog is the last line of defence, so this layer exists to keep obviously
 * dangerous requests from ever reaching it: package blocklist first, then shape checks that
 * the accessibility API would otherwise reject deep inside a gesture dispatch.
 */
object ScreenControlPolicy {

    /**
     * Package fragments that never accept automated input.
     *
     * Deliberately keyword-based rather than an exhaustive package list: banking and
     * payment apps change their ids constantly, and a false positive here only means the
     * user has to tap the screen themselves.
     */
    val DEFAULT_BLOCKLIST = listOf(
        "bank",
        "pay",
        "wallet",
        "alipay",
        "wechat.pay",
        "unionpay",
        "icbc",
        "ccb",
        "abchina",
        "boc.cn",
        "cmbchina",
        "spdb",
        "cebbank",
        "psbc",
        "citic",
        "cmbc",
        "pingan",
        "安",
        "银行",
        "支付",
        "支付宝",
        "钱包",
    )

    sealed interface Verdict {
        data object Allowed : Verdict
        data class Blocked(val reason: String) : Verdict
    }

    fun check(
        request: ScreenActionRequest,
        currentPackage: String?,
        blocklist: List<String>,
        maxActionsPerSession: Int,
        actionsThisSession: Int,
        /** This app's own package: the agent must never drive our own buttons. */
        selfPackage: String? = null,
    ): Verdict {
        if (actionsThisSession >= maxActionsPerSession) {
            return Verdict.Blocked("本次会话动作次数已达上限（$maxActionsPerSession 次），请重新开始")
        }
        val pkg = currentPackage?.lowercase()
        if (pkg != null && pkg.isNotBlank()) {
            val hit = blocklist.firstOrNull { fragment ->
                fragment.isNotBlank() && pkg.contains(fragment.lowercase())
            }
            if (hit != null) {
                return Verdict.Blocked("当前应用匹配禁止规则「$hit」，不允许自动操作")
            }
            // Coordinates are read off the target app's screen; if this app is in front the
            // same tap would press our own buttons (delete project, uninstall, settings).
            if (needsForeground(request.kind) && selfPackage != null && pkg == selfPackage.lowercase()) {
                return Verdict.Blocked("当前前台是本应用，请先切换到你要操作的那个应用")
            }
        }
        return when (request.kind) {
            ScreenActionKind.TAP, ScreenActionKind.TYPE -> {
                if (pkg == null) {
                    Verdict.Blocked("无法识别当前应用，出于安全考虑不再执行")
                } else {
                    Verdict.Allowed
                }
            }
            ScreenActionKind.SWIPE -> {
                if (request.x == request.x2 && request.y == request.y2) {
                    Verdict.Blocked("滑动起点和终点相同")
                } else {
                    Verdict.Allowed
                }
            }
            ScreenActionKind.WAIT -> Verdict.Allowed
            else -> Verdict.Allowed
        }
    }

    /** Turns an approved request into the result the agent will read back. */
    fun execute(
        request: ScreenActionRequest,
        approved: Boolean,
        blockReason: String?,
        /** App the request was offered for; re-checked so a switched screen cannot be hit. */
        expectedPackage: String? = null,
    ): ScreenActionResult {
        if (blockReason != null) {
            return ScreenActionResult(request.id, ScreenActionStatus.BLOCKED, blockReason)
        }
        if (!approved) {
            return ScreenActionResult(request.id, ScreenActionStatus.DENIED, "用户拒绝了这次操作")
        }
        if (needsForeground(request.kind) && !expectedPackage.isNullOrBlank()) {
            val foreground = ScreenController.awaitForeground(expectedPackage)
            if (foreground != expectedPackage) {
                return ScreenActionResult(
                    request.id,
                    ScreenActionStatus.FAILED,
                    "前台应用已经变成 ${foreground ?: "未知"}，这次操作没有执行",
                )
            }
        }
        val before = System.currentTimeMillis()
        val outcome = ScreenController.perform(request)
        if (outcome.status != ScreenActionStatus.OK) {
            return outcome.copy(snapshot = ScreenReaderService.currentText())
        }
        // The service refreshes on accessibility events, which arrive after a delay. Without
        // this the agent would read the screen from before its own action and retry blindly.
        val snapshot = ScreenReaderService.awaitSnapshotAfter(
            after = before,
            timeoutMs = 1_800L,
            expectedPackage = expectedPackage,
        )
        return outcome.copy(snapshot = snapshot)
    }

    /** Actions aimed at coordinates or text only make sense on the app they were offered for. */
    private fun needsForeground(kind: ScreenActionKind): Boolean = when (kind) {
        ScreenActionKind.TAP, ScreenActionKind.SWIPE, ScreenActionKind.TYPE -> true
        else -> false
    }
}
