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
    ): ScreenActionResult {
        if (blockReason != null) {
            return ScreenActionResult(request.id, ScreenActionStatus.BLOCKED, blockReason)
        }
        if (!approved) {
            return ScreenActionResult(request.id, ScreenActionStatus.DENIED, "用户拒绝了这次操作")
        }
        val outcome = ScreenController.perform(request)
        val snapshot = ScreenReaderService.currentText()
        return outcome.copy(snapshot = snapshot)
    }
}
