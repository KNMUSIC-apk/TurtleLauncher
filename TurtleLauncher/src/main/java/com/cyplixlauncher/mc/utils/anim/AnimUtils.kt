package com.cyplixlauncher.mc.utils.anim

import android.view.View
import com.endiq.anim.animations.Animations
import com.cyplixlauncher.mc.setting.AllSettings
import com.cyplixlauncher.mc.task.Task
import com.cyplixlauncher.mc.utils.anim.ViewAnimUtils.Companion.setViewAnim


class AnimUtils {
    companion object {
        /** Default duration for the overloads without one: the user's animation speed setting. */
        private fun defaultDuration(): Int = AllSettings.animationSpeed.getValue()

        @JvmStatic
        fun setVisibilityAnim(view: View, shouldShow: Boolean) {
            setVisibilityAnim(view, shouldShow, defaultDuration(), null)
        }

        @JvmStatic
        fun setVisibilityAnim(view: View, shouldShow: Boolean, listener: AnimationListener?) {
            setVisibilityAnim(view, shouldShow, defaultDuration(), listener)
        }

        @JvmStatic
        fun setVisibilityAnim(view: View, shouldShow: Boolean, duration: Int) {
            setVisibilityAnim(view, shouldShow, duration, null)
        }

        @JvmStatic
        fun setVisibilityAnim(
            view: View,
            shouldShow: Boolean,
            duration: Int,
            listener: AnimationListener?
        ) {
            setVisibilityAnim(view, 0, shouldShow, duration, listener)
        }

        @JvmStatic
        fun playVisibilityAnim(view: View, visible: Boolean) {
            val targetVisibility = if (visible) View.VISIBLE else View.GONE
            if (view.visibility == targetVisibility) return

            setViewAnim(view, if (visible) TurtleTransitions.enter() else TurtleTransitions.exit(),
                (AllSettings.animationSpeed.getValue() * 0.7).toLong(),
                { view.visibility = View.VISIBLE },
                { view.visibility = if (visible) View.VISIBLE else View.GONE })
        }

        /**
         * Convenience helper for hide animations.
         * @param view the view to animate
         * @param startDelay delay before the animation starts
         * @param shouldShow true: show, false: hide
         * @param duration how long the animation runs
         * @param listener animation listener for the start/end callbacks
         */
        @JvmStatic
        fun setVisibilityAnim(
            view: View,
            startDelay: Int,
            shouldShow: Boolean,
            duration: Int,
            listener: AnimationListener?
        ) {
            listener?.onStart()

            if (shouldShow && view.visibility != View.VISIBLE) {
                fadeAnim(view, startDelay.toLong(), 0f, 1f, duration) {
                    view.visibility = View.VISIBLE
                    listener?.onEnd()
                }
            } else if (!shouldShow && view.visibility != View.GONE) {
                fadeAnim(view, startDelay.toLong(), view.alpha, 0f, duration) {
                    view.visibility = View.GONE
                    listener?.onEnd()
                }
            }
        }

        /**
         * Convenience helper for fade in/out animations.
         * @param view the view to animate
         * @param startDelay delay before the animation starts
         * @param begin starting alpha
         * @param end ending alpha
         * @param duration how long the animation runs
         * @param endAction task executed when the animation ends
         */
        @JvmStatic
        fun fadeAnim(
            view: View,
            startDelay: Long,
            begin: Float,
            end: Float,
            duration: Int,
            endAction: Runnable?
        ) {
            if ((view.visibility != View.VISIBLE && end == 0f) || (view.visibility == View.VISIBLE && end == 1f)) {
                endAction?.let { r -> Task.runTask { r.run() }.execute() }
                return
            }
            // Respect the animation toggle: land on the final state straight away when animations are off.
            if (!TurtleTransitions.isEnabled()) {
                view.visibility = View.VISIBLE
                view.alpha = end
                endAction?.run()
                return
            }
            view.visibility = View.VISIBLE
            view.alpha = begin
            view.animate()
                .alpha(end)
                .setStartDelay(startDelay)
                .setDuration(duration.toLong())
                .withEndAction(endAction)
        }
    }

    interface AnimationListener {
        fun onStart()
        fun onEnd()
    }
}
