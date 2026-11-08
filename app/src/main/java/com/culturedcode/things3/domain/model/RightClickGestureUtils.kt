package com.culturedcode.things3.domain.model

import android.view.MotionEvent

/**
 * Pure domain helper for detecting right-click / secondary mouse / two-finger Chromebook trackpad
 * context click events across Android, ChromeOS ARC/ARCVM, and external USB/Bluetooth mice.
 */
object RightClickGestureUtils {

    private const val SECONDARY_BUTTON_MASK: Int =
        MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_SECONDARY

    fun isSecondaryMouseOrContextClick(
        isSecondaryButtonFlag: Boolean,
        motionEventActionMasked: Int,
        motionEventButtonState: Int,
        motionEventActionButton: Int
    ): Boolean {
        if (isSecondaryButtonFlag) return true
        if ((motionEventButtonState and SECONDARY_BUTTON_MASK) != 0) return true
        if ((motionEventActionButton and SECONDARY_BUTTON_MASK) != 0) return true
        if (motionEventActionMasked == MotionEvent.ACTION_BUTTON_PRESS ||
            motionEventActionMasked == MotionEvent.ACTION_BUTTON_RELEASE
        ) {
            if ((motionEventActionButton and SECONDARY_BUTTON_MASK) != 0 ||
                (motionEventButtonState and SECONDARY_BUTTON_MASK) != 0
            ) {
                return true
            }
        }
        return false
    }

    fun isRightClickEvent(
        isPressEvent: Boolean,
        isReleaseEvent: Boolean,
        isSecondaryButtonFlag: Boolean,
        previousButtonsSecondary: Boolean,
        motionEventActionMasked: Int,
        motionEventButtonState: Int,
        motionEventActionButton: Int
    ): Boolean {
        val detectedSecondaryNow = isSecondaryMouseOrContextClick(
            isSecondaryButtonFlag = isSecondaryButtonFlag,
            motionEventActionMasked = motionEventActionMasked,
            motionEventButtonState = motionEventButtonState,
            motionEventActionButton = motionEventActionButton
        )
        if (isPressEvent && detectedSecondaryNow) {
            return true
        }
        if (detectedSecondaryNow && !previousButtonsSecondary) {
            return true
        }
        if (motionEventActionMasked == MotionEvent.ACTION_BUTTON_PRESS &&
            isSecondaryMouseOrContextClick(
                isSecondaryButtonFlag = false,
                motionEventActionMasked = motionEventActionMasked,
                motionEventButtonState = motionEventButtonState,
                motionEventActionButton = motionEventActionButton
            )
        ) {
            return true
        }
        if (isReleaseEvent && (modalSecondaryRelease(previousButtonsSecondary, motionEventActionButton))) {
            return true
        }
        return false
    }

    private fun modalSecondaryRelease(
        previousButtonsSecondary: Boolean,
        motionEventActionButton: Int
    ): Boolean {
        return previousButtonsSecondary || (motionEventActionButton and SECONDARY_BUTTON_MASK) != 0
    }
}
