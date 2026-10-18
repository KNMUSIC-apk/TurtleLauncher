package org.lwjgl.glfw;

import android.content.ClipData;
import android.content.ClipDescription;
import android.view.Choreographer;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import net.endiq.launcher.GrabListener;
import net.endiq.launcher.LwjglGlfwKeycode;
import net.endiq.launcher.MainActivity;

import java.util.ArrayList;

import dalvik.annotation.optimization.CriticalNative;

public class CallbackBridge {
    public static final Choreographer sChoreographer = Choreographer.getInstance();
    private static boolean isGrabbing = false;
    private static final ArrayList<GrabListener> grabListeners = new ArrayList<>();
    
    public static final int CLIPBOARD_COPY = 2000;
    public static final int CLIPBOARD_PASTE = 2001;
    public static final int CLIPBOARD_OPEN = 2002;
    
    public static volatile int windowWidth, windowHeight;
    public static volatile int physicalWidth, physicalHeight;
    public static float mouseX, mouseY;
    public volatile static boolean holdingAlt, holdingCapslock, holdingCtrl,
            holdingNumlock, holdingShift;

    public static void putMouseEventWithCoords(int button, float x, float y) {
        putMouseEventWithCoords(button, true, x, y);
        sChoreographer.postFrameCallbackDelayed(l -> putMouseEventWithCoords(button, false, x, y), 33);
    }
    
    public static void putMouseEventWithCoords(int button, boolean isDown, float x, float y /* , int dz, long nanos */) {
        sendCursorPos(x, y);
        sendMouseKeycode(button, CallbackBridge.getCurrentMods(), isDown);
    }


    public static void sendCursorPos(float x, float y) {
        mouseX = x;
        mouseY = y;
        nativeSendCursorPos(mouseX, mouseY);
    }

    public static void sendKeycode(int keycode, char keychar, int scancode, int modifiers, boolean isDown) {
        com.cyplixlauncher.mc.feature.inputstats.InputStatsTracker.onKeyEvent(keycode, isDown);
        // TODO CHECK: This may cause input issue, not receive input!
        if(keycode != 0)  nativeSendKey(keycode,scancode,isDown ? 1 : 0, modifiers);
        if(isDown && keychar != '\u0000') {
            nativeSendCharMods(keychar,modifiers);
            nativeSendChar(keychar);
        }
    }

    public static void sendChar(char keychar, int modifiers){
        nativeSendCharMods(keychar,modifiers);
        nativeSendChar(keychar);
    }

    public static void sendKeyPress(int keyCode, int modifiers, boolean status) {
        sendKeyPress(keyCode, 0, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, int scancode, int modifiers, boolean status) {
        sendKeyPress(keyCode, '\u0000', scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, char keyChar, int scancode, int modifiers, boolean status) {
        CallbackBridge.sendKeycode(keyCode, keyChar, scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode) {
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), true);
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), false);
    }

    public static void sendMouseButton(int button, boolean status) {
        CallbackBridge.sendMouseKeycode(button, CallbackBridge.getCurrentMods(), status);
    }

    public static void sendMouseKeycode(int button, int modifiers, boolean isDown) {
        com.cyplixlauncher.mc.feature.inputstats.InputStatsTracker.onMouseEvent(button, isDown);
        // if (isGrabbing()) DEBUG_STRING.append("MouseGrabStrace: " + android.util.Log.getStackTraceString(new Throwable()) + "\n");
        nativeSendMouseButton(button, isDown ? 1 : 0, modifiers);
    }

    public static void sendMouseKeycode(int keycode) {
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), true);
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), false);
    }
    
    public static void sendScroll(double xoffset, double yoffset) {
        nativeSendScroll(xoffset, yoffset);
    }

    /**
     * Wheel events can arrive twice: once through the generic motion path and once through the
     * captured-pointer path, both reading the same MotionEvent. Drop a repeat of the same event,
     * and keep the fractional part of small deltas so they add up to whole notches (the native
     * side truncates each value to an int, so a 0.5 notch would otherwise be lost).
     */
    private static long sLastScrollEventTime = Long.MIN_VALUE;
    private static float sLastScrollH = 0f;
    private static float sLastScrollV = 0f;
    private static double sScrollRemainderX = 0d;
    private static double sScrollRemainderY = 0d;

    public static void sendScrollEvent(long eventTime, float hScroll, float vScroll) {
        if (eventTime == sLastScrollEventTime && hScroll == sLastScrollH && vScroll == sLastScrollV) return;
        sLastScrollEventTime = eventTime;
        sLastScrollH = hScroll;
        sLastScrollV = vScroll;

        sScrollRemainderX += hScroll;
        sScrollRemainderY += vScroll;
        double wholeX = (int) sScrollRemainderX;
        double wholeY = (int) sScrollRemainderY;
        sScrollRemainderX -= wholeX;
        sScrollRemainderY -= wholeY;
        if (wholeX == 0 && wholeY == 0) return;
        sendScroll(wholeX, wholeY);
    }

    public static void sendUpdateWindowSize(int w, int h) {
        nativeSendScreenSize(w, h);
    }

    public static boolean isGrabbing() {
        // Avoid going through the JNI each time.
        return isGrabbing;
    }

    // Called from JRE side
    @SuppressWarnings("unused")
    public static @Nullable String accessAndroidClipboard(int type, String copy) {
        try {
            switch (type) {
                case CLIPBOARD_COPY:
                    if (MainActivity.GLOBAL_CLIPBOARD != null && copy != null) {
                        MainActivity.GLOBAL_CLIPBOARD.setPrimaryClip(ClipData.newPlainText("Copy", copy));
                    }
                    return null;

                case CLIPBOARD_PASTE: {
                    android.content.ClipboardManager clipboard = MainActivity.GLOBAL_CLIPBOARD;
                    if (clipboard == null || !clipboard.hasPrimaryClip()) return "";
                    ClipDescription description = clipboard.getPrimaryClipDescription();
                    if (description == null || !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return "";
                    ClipData clip = clipboard.getPrimaryClip(); // may STILL be null: race or background restriction
                    if (clip == null || clip.getItemCount() == 0) return "";
                    ClipData.Item item = clip.getItemAt(0);
                    if (item == null) return "";
                    CharSequence text = item.getText(); // null for URI-only items
                    return text != null ? text.toString() : "";
                }

                case CLIPBOARD_OPEN:
                    MainActivity.openLink(copy);
                    return null;
                default: return null;
            }
        } catch (Throwable t) {
            android.util.Log.w("CallbackBridge", "Clipboard access from the game failed; continuing", t);
            return type == CLIPBOARD_PASTE ? "" : null;
        }
    }


    public static int getCurrentMods() {
        int currMods = 0;
        if (holdingAlt) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_ALT;
        } if (holdingCapslock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CAPS_LOCK;
        } if (holdingCtrl) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CONTROL;
        } if (holdingNumlock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_NUM_LOCK;
        } if (holdingShift) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_SHIFT;
        }
        return currMods;
    }

    public static void setModifiers(int keyCode, boolean isDown){
        switch (keyCode){
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT:
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT:
                CallbackBridge.holdingShift = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL:
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL:
                CallbackBridge.holdingCtrl = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT:
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT:
                CallbackBridge.holdingAlt = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK:
                CallbackBridge.holdingCapslock = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK:
                CallbackBridge.holdingNumlock = isDown;
        }
    }

    //Called from JRE side
    @SuppressWarnings("unused")
    private static void onGrabStateChanged(final boolean grabbing) {
        isGrabbing = grabbing;
        sChoreographer.postFrameCallbackDelayed((time) -> {
            // If the grab re-changed, skip notify process
            if(isGrabbing != grabbing) return;

            System.out.println("Grab changed : " + grabbing);
            synchronized (grabListeners) {
                for (GrabListener g : grabListeners) {
                    try {
                        g.onGrabState(grabbing);
                    } catch (Throwable t) {
                        android.util.Log.w("CallbackBridge", "Grab listener failed", t);
                    }
                }
            }

        }, 16);

    }
    public static void addGrabListener(GrabListener listener) {
        synchronized (grabListeners) {
            listener.onGrabState(isGrabbing);
            grabListeners.add(listener);
        }
    }
    public static void removeGrabListener(GrabListener listener) {
        synchronized (grabListeners) {
            grabListeners.remove(listener);
        }
    }

    @Keep @CriticalNative public static native void nativeSetUseInputStackQueue(boolean useInputStackQueue);

    @Keep @CriticalNative private static native boolean nativeSendChar(char codepoint);
    // GLFW: GLFWCharModsCallback deprecated, but is Minecraft still use?
    @Keep @CriticalNative private static native boolean nativeSendCharMods(char codepoint, int mods);
    @Keep @CriticalNative private static native void nativeSendKey(int key, int scancode, int action, int mods);
    // private static native void nativeSendCursorEnter(int entered);
    @Keep @CriticalNative private static native void nativeSendCursorPos(float x, float y);
    @Keep @CriticalNative private static native void nativeSendMouseButton(int button, int action, int mods);
    @Keep @CriticalNative private static native void nativeSendScroll(double xoffset, double yoffset);
    @Keep @CriticalNative private static native void nativeSendScreenSize(int width, int height);
    @Keep public static native void nativeSetWindowAttrib(int attrib, int value);
    @Keep public static native int getCurrentFps();

    static {
        System.loadLibrary("pojavexec");
    }
}

