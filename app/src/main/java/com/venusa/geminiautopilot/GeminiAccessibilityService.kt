package com.venusa.geminiautopilot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GeminiAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastObservedText = ""
    private var completionPending = false
    private var lastAutoNextAt = 0L

    private val sendNext = Runnable {
        if (!isMonitoring || !completionPending) return@Runnable
        completionPending = false
        attemptToSendNext()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isMonitoring || event == null) return
        if (event.packageName?.toString() != GEMINI_PACKAGE) return

        val root = rootInActiveWindow ?: return
        val visibleText = collectText(root).joinToString(" ").lowercase()
        val now = System.currentTimeMillis()

        val generating = GENERATING_MARKERS.any { visibleText.contains(it) }
        val completionCue = COMPLETION_MARKERS.any { visibleText.contains(it) }

        if (generating) {
            completionPending = false
            handler.removeCallbacks(sendNext)
        } else if (completionCue && visibleText != lastObservedText &&
            now - lastAutoNextAt > 12_000L) {
            completionPending = true
            handler.removeCallbacks(sendNext)
            handler.postDelayed(sendNext, completionWaitMs)
        }
        lastObservedText = visibleText
    }

    private fun collectText(node: AccessibilityNodeInfo): List<String> {
        val found = mutableListOf<String>()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(found::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(found::add)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                found.addAll(collectText(child))
                child.recycle()
            }
        }
        return found
    }

    private fun attemptToSendNext() {
        val root = rootInActiveWindow ?: return
        val fields = mutableListOf<AccessibilityNodeInfo>()
        findEditable(root, fields)
        val input = fields.lastOrNull()
        if (input == null) {
            fields.forEach { it.recycle() }
            return
        }

        val args = android.os.Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            "Next"
        )
        val entered = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        fields.forEach { it.recycle() }
        if (!entered) return

        val refreshedRoot = rootInActiveWindow ?: return
        val sendButton = findSendButton(refreshedRoot)
        if (sendButton != null) {
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            sendButton.recycle()
            lastAutoNextAt = System.currentTimeMillis()
        }
    }

    private fun findEditable(node: AccessibilityNodeInfo, output: MutableList<AccessibilityNodeInfo>) {
        if (node.isEditable && node.isEnabled) output.add(AccessibilityNodeInfo.obtain(node))
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findEditable(child, output)
                child.recycle()
            }
        }
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val label = (node.text?.toString() + " " + node.contentDescription?.toString()).lowercase()
        if (node.isClickable && node.isEnabled &&
            listOf("send", "submit").any { label.contains(it) }) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findSendButton(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    override fun onInterrupt() {
        handler.removeCallbacks(sendNext)
    }

    override fun onDestroy() {
        handler.removeCallbacks(sendNext)
        super.onDestroy()
    }

    companion object {
        const val GEMINI_PACKAGE = "com.google.android.apps.bard"
        @Volatile var isMonitoring = false
        @Volatile var completionWaitMs = 8_000L

        private val GENERATING_MARKERS = listOf(
            "stop generating", "stop response", "stop answering", "cancel response"
        )
        private val COMPLETION_MARKERS = listOf(
            "regenerate", "good response", "bad response", "copy response",
            "share response", "read aloud"
        )
    }
}
