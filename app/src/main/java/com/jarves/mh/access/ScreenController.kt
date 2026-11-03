package com.jarves.mh.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.jarves.mh.model.ScreenActionKind
import com.jarves.mh.model.ScreenActionRequest
import com.jarves.mh.model.ScreenActionResult
import com.jarves.mh.model.ScreenActionStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Executes one approved screen action.
 *
 * Every entry point assumes the user has already confirmed this exact request: nothing
 * here asks permission again, and nothing here decides whether an action is acceptable —
 * that is [ScreenControlPolicy]'s job, checked before this is called.
 */
object ScreenController {

    /** Set by the service while it is connected, cleared when the user turns it off. */
    @Volatile
    var service: AccessibilityService? = null

    private const val GESTURE_TIMEOUT_MS = 5_000L

    fun perform(request: ScreenActionRequest): ScreenActionResult {
        val svc = service
            ?: return ScreenActionResult(
                request.id,
                ScreenActionStatus.FAILED,
                "无障碍服务未连接",
            )
        return try {
            when (request.kind) {
                ScreenActionKind.TAP -> dispatchTap(svc, request)
                ScreenActionKind.SWIPE -> dispatchSwipe(svc, request)
                ScreenActionKind.BACK -> global(svc, AccessibilityService.GLOBAL_ACTION_BACK, request)
                ScreenActionKind.HOME -> global(svc, AccessibilityService.GLOBAL_ACTION_HOME, request)
                ScreenActionKind.RECENTS -> global(svc, AccessibilityService.GLOBAL_ACTION_RECENTS, request)
                ScreenActionKind.SCROLL_FORWARD -> scroll(request, forward = true)
                ScreenActionKind.SCROLL_BACKWARD -> scroll(request, forward = false)
                ScreenActionKind.TYPE -> typeText(request)
                ScreenActionKind.WAIT -> {
                    Thread.sleep(request.durationMs.toLong())
                    ScreenActionResult(request.id, ScreenActionStatus.OK, "已等待")
                }
            }
        } catch (error: Throwable) {
            ScreenActionResult(
                request.id,
                ScreenActionStatus.FAILED,
                error.message ?: error::class.java.simpleName,
            )
        }
    }

    private fun global(
        svc: AccessibilityService,
        action: Int,
        request: ScreenActionRequest,
    ): ScreenActionResult {
        val ok = svc.performGlobalAction(action)
        return ScreenActionResult(
            request.id,
            if (ok) ScreenActionStatus.OK else ScreenActionStatus.FAILED,
            if (ok) "" else "系统拒绝了这个动作",
        )
    }

    /**
     * Scrolls the first scrollable node under the active window.
     *
     * AccessibilityService has no global scroll action, so this has to walk to a scrollable
     * node and ask it directly. If nothing scrollable is on screen the agent gets FAILED
     * rather than a silent no-op.
     */
    private fun scroll(request: ScreenActionRequest, forward: Boolean): ScreenActionResult {
        val svc = service
            ?: return ScreenActionResult(request.id, ScreenActionStatus.FAILED, "无障碍服务未连接")
        val root = svc.rootInActiveWindow
            ?: return ScreenActionResult(request.id, ScreenActionStatus.FAILED, "没有可操作的窗口")
        val scrollable = runCatching { findScrollable(root) }.getOrNull()
        if (scrollable == null) {
            runCatching { root.recycle() }
            return ScreenActionResult(request.id, ScreenActionStatus.FAILED, "当前界面没有可滚动区域")
        }
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val ok = scrollable.performAction(action)
        runCatching { scrollable.recycle() }
        runCatching { root.recycle() }
        return ScreenActionResult(
            request.id,
            if (ok) ScreenActionStatus.OK else ScreenActionStatus.FAILED,
            if (ok) "" else "已经滑到底了，或者这个区域不支持滚动",
        )
    }

    /** Depth-first search for the first node that reports itself scrollable. */
    private fun findScrollable(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 25) return null
        if (node.isScrollable) return node
        for (index in 0 until node.childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            val found = findScrollable(child, depth + 1)
            if (found != null) return found
            runCatching { child.recycle() }
        }
        return null
    }

    private fun dispatchTap(svc: AccessibilityService, request: ScreenActionRequest): ScreenActionResult {
        val path = Path().apply { moveTo(request.x.toFloat(), request.y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, request.durationMs.toLong())
        return dispatch(svc, GestureDescription.Builder().addStroke(stroke).build(), request)
    }

    private fun dispatchSwipe(svc: AccessibilityService, request: ScreenActionRequest): ScreenActionResult {
        val path = Path().apply {
            moveTo(request.x.toFloat(), request.y.toFloat())
            lineTo(request.x2.toFloat(), request.y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, request.durationMs.toLong())
        return dispatch(svc, GestureDescription.Builder().addStroke(stroke).build(), request)
    }

    private fun dispatch(
        svc: AccessibilityService,
        gesture: GestureDescription,
        request: ScreenActionRequest,
    ): ScreenActionResult {
        val latch = CountDownLatch(1)
        var completed = false
        val accepted = svc.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(description: GestureDescription?) {
                    completed = true
                    latch.countDown()
                }

                override fun onCancelled(description: GestureDescription?) {
                    latch.countDown()
                }
            },
            null,
        )
        if (!accepted) {
            return ScreenActionResult(request.id, ScreenActionStatus.FAILED, "系统没有接受这个手势")
        }
        val finished = latch.await(GESTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return when {
            !finished -> ScreenActionResult(request.id, ScreenActionStatus.TIMEOUT, "手势超时")
            completed -> ScreenActionResult(request.id, ScreenActionStatus.OK, "")
            else -> ScreenActionResult(request.id, ScreenActionStatus.FAILED, "手势被系统取消")
        }
    }

    /**
     * Types into whatever field currently has input focus.
     *
     * Refuses password fields outright: the app cannot tell whether the user would want a
     * secret typed for them, and a wrong guess is unrecoverable.
     */
    private fun typeText(request: ScreenActionRequest): ScreenActionResult {
        val svc = service
            ?: return ScreenActionResult(request.id, ScreenActionStatus.FAILED, "无障碍服务未连接")
        val focused = svc.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return ScreenActionResult(request.id, ScreenActionStatus.FAILED, "当前没有获得焦点的输入框")
        if (focused.isPassword) {
            focused.recycle()
            return ScreenActionResult(
                request.id,
                ScreenActionStatus.BLOCKED,
                "目标是密码输入框，已拒绝",
            )
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, request.text)
        }
        val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()
        return ScreenActionResult(
            request.id,
            if (ok) ScreenActionStatus.OK else ScreenActionStatus.FAILED,
            if (ok) "" else "输入框拒绝了这个操作",
        )
    }

    /** Package name of the window the user is looking at, for the blocklist check. */
    fun currentPackage(): String? = runCatching {
        service?.rootInActiveWindow?.packageName?.toString()
    }.getOrNull()
}
