package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import org.lwjgl.glfw.*;

import java.nio.Buffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static jvm.JVM32.log;
import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;

public class LWJGLxGLFW {

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwInit_Z")
    public static boolean org_lwjgl_glfw_GLFW_glfwInit_Z() {
        return true;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwGetWindowPos_JAIAIV")
    public static void org_lwjgl_glfw_GLFW_glfwGetWindowPos_JAIAIV(long window, int[] x, int[] y) {
        if (x != null && x.length > 0) x[0] = 0;
        if (y != null && y.length > 0) y[0] = 0;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetCursorPos_JDDV")
    public static void org_lwjgl_glfw_GLFW_glfwSetCursorPos_JDDV(long window, double x, double y) {
        log("Setting cursor is not supported!");
    }


    @NoThrow
    @JavaScript(code = "" +
            "canvas.requestPointerLock = canvas.requestPointerLock || canvas.mozRequestPointerLock;\n" +
            "canvas.requestPointerLock()")
    public static native void disableCursor();

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetInputMode_JIIV")
    public static void org_lwjgl_glfw_GLFW_glfwSetInputMode_JIIV(long window, int key, int value) {
        if (key == GLFW.GLFW_CURSOR && value == GLFW.GLFW_CURSOR_DISABLED) {
            disableCursor();
        } // else unknown / or disabling full screen
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwGetWindowMonitor_JJ")
    public static long org_lwjgl_glfw_GLFW_glfwGetWindowMonitor_JJ(long window) {
        return window;
    }


    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwCreateStandardCursor_IJ")
    public static long org_lwjgl_glfw_GLFW_glfwCreateStandardCursor_IJ(int id) {
        return id;
    }

    @Alias(names = "org_lwjgl_system_MemoryUtil_memAllocShort_ILjava_nio_ShortBuffer")
    public static ShortBuffer MemoryUtil_memAllocShort(int size) {
        return ShortBuffer.allocate(size);
    }

    @Alias(names = "org_lwjgl_system_MemoryUtil_memFree_Ljava_nio_BufferV")
    public static void MemoryUtil_memFree(Buffer buffer) {
        // will be collected by GC, when we have it
    }

    // not supported
    @NoThrow
    @JavaScript(code = "")
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetCursor_JJV")
    public static native void org_lwjgl_glfw_GLFW_glfwSetCursor_JJV(long window, long cursor);

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwWaitEventsTimeout_DV")
    public static void GLFW_glfwWaitEventsTimeout_DV(double timeout) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSwapBuffers_JV")
    public static void GLFW_glfwSwapBuffers(long window) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwWindowShouldClose_JZ")
    public static boolean GLFW_glfwWindowShouldClose(long window) {
        return false;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetWindowShouldClose_JZV")
    public static void GLFW_glfwSetWindowShouldClose(long window, boolean shouldClose) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwCreateWindow_IILjava_lang_CharSequenceJJJ")
    public static long GLFW_glfwCreateWindow(int width, int height, CharSequence title, long monitor, long share) {
        if (title != null) setTitle(title.toString());
        return 0x100;
    }

    @NoThrow
    @JavaScript(code = "document.title=str(arg0)")
    public static native void setTitle(String title);

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwShowWindow_JV")
    public static void GLFW_glfwShowWindow(long window) {

    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwMakeContextCurrent_JV")
    public static void GLFW_glfwMakeContextCurrent(long window) {

    }

    @NoThrow
    @JavaScript(code = "")
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSwapInterval_IV")
    public static native void GLFW_glfwSwapInterval(int interval);

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetWindowPos_JIIV")
    public static void GLFW_glfwSetWindowPos(long window, int x, int y) {

    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwWindowHint_IIV")
    public static void GLFW_glfwWindowHint(int key, int value) {

    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetDropCallback_JLorg_lwjgl_glfw_GLFWDropCallbackILorg_lwjgl_glfw_GLFWDropCallback")
    public static GLFWDropCallback org_lwjgl_glfw_GLFW_glfwSetDropCallback(long window, GLFWDropCallbackI callback) {
        return null;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetCharModsCallback_JLorg_lwjgl_glfw_GLFWCharModsCallbackILorg_lwjgl_glfw_GLFWCharModsCallback")
    public static GLFWCharModsCallback setCallback(long window, GLFWCharModsCallbackI callback) {
        // todo set callback in JavaScript
        return null;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetCursorPosCallback_JLorg_lwjgl_glfw_GLFWCursorPosCallbackILorg_lwjgl_glfw_GLFWCursorPosCallback")
    public static GLFWCursorPosCallback setCallback(long window, GLFWCursorPosCallbackI callback) {
        // todo set callback in JavaScript
        return null;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetMouseButtonCallback_JLorg_lwjgl_glfw_GLFWMouseButtonCallbackILorg_lwjgl_glfw_GLFWMouseButtonCallback")
    public static GLFWMouseButtonCallback setCallback(long window, GLFWMouseButtonCallbackI callback) {
        // todo set callback in JavaScript
        return null;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetScrollCallback_JLorg_lwjgl_glfw_GLFWScrollCallbackILorg_lwjgl_glfw_GLFWScrollCallback")
    public static GLFWScrollCallback setCallback(long window, GLFWScrollCallbackI callback) {
        // todo set callback in JavaScript
        return null;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetKeyCallback_JLorg_lwjgl_glfw_GLFWKeyCallbackILorg_lwjgl_glfw_GLFWKeyCallback")
    public static GLFWKeyCallback setKeyCallback(long window, GLFWKeyCallbackI callback) {
        // todo set callback in JavaScript
        return null;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwGetPrimaryMonitor_J")
    public static long org_lwjgl_glfw_GLFW_glfwGetPrimaryMonitor_J() {
        return 0L;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwGetVideoMode_JLorg_lwjgl_glfw_GLFWVidMode")
    public static GLFWVidMode GLFW_glfwGetVideoMode(long monitor) {
        // would be an error, but whatever, we control the engine
        return null;
    }


    @NoThrow
    @JavaScript(code = "return canvas.width")
    public static native int getWindowWidth();

    @NoThrow
    @JavaScript(code = "return canvas.height")
    public static native int getWindowHeight();

    @Alias(names = "org_lwjgl_glfw_GLFW_nglfwGetFramebufferSize_JJJV")
    public static void nglfwGetFramebufferSize(long window, long width, long height) {
        IntBuffer buffer = ptrTo((int) width);
        buffer.put(0, getWindowWidth());
        buffer.put(1, getWindowHeight());
    }

    @Alias(names = "org_lwjgl_glfw_GLFW_glfwGetFramebufferSize_JAIAIV")
    public static void org_lwjgl_glfw_GLFW_glfwGetFramebufferSize_JAIAIV(long window, int[] w, int[] h) {
        if (w != null && w.length > 0) w[0] = getWindowWidth();
        if (h != null && h.length > 0) h[0] = getWindowHeight();
    }

    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetWindowTitle_JLjava_lang_CharSequenceV")
    public static void setWindowTitle(long window, CharSequence title) {
        setTitle(title.toString());
    }

    @NoThrow
    @JavaScript(code = "return mouseX")
    private static native double getMouseX();

    @NoThrow
    @JavaScript(code = "return mouseY")
    private static native double getMouseY();

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwGetCursorPos_JADADV")
    public static void org_lwjgl_glfw_GLFW_glfwGetCursorPos_JADADV(long window, double[] x, double[] y) {
        if (x != null) x[0] = getMouseX();
        if (y != null) y[0] = getMouseY();
    }

    @Alias(names = "static_org_lwjgl_glfw_GLFWDropCallbackI_V")
    private static void static_org_lwjgl_glfw_GLFWDropCallbackI_V() {
    }

    @Alias(names = "static_org_lwjgl_glfw_GLFWErrorCallbackI_V")
    private static void static_org_lwjgl_glfw_GLFWErrorCallbackI_V() {
    }

    @Alias(names = "static_org_lwjgl_glfw_GLFWGamepadState_V")
    private static void static_org_lwjgl_glfw_GLFWGamepadState_V() {
    }

    @Alias(names = "static_org_lwjgl_glfw_GLFWCursorPosCallbackI_V")
    private static void static_org_lwjgl_glfw_GLFWCursorPosCallbackI_V() {
    }

    @Alias(names = "static_org_lwjgl_glfw_GLFWCharModsCallbackI_V")
    private static void static_org_lwjgl_glfw_GLFWCharModsCallbackI_V() {
    }

    @Alias(names = "static_org_lwjgl_Version_V")
    private static void static_org_lwjgl_Version_V() {
    }

    @Alias(names = "static_org_lwjgl_VersionXBuildType_V")
    private static void static_org_lwjgl_VersionXBuildType_V() {
    }

    @Alias(names = "static_org_lwjgl_glfw_GLFWKeyCallbackI_V")
    private static void static_org_lwjgl_glfw_GLFWKeyCallbackI_V() {
    }

    @Alias(names = "static_org_lwjgl_glfw_GLFWMouseButtonCallbackI_V")
    private static void static_org_lwjgl_glfw_GLFWMouseButtonCallbackI_V() {
    }

    @Alias(names = "static_org_lwjgl_glfw_GLFWScrollCallbackI_V")
    private static void static_org_lwjgl_glfw_GLFWScrollCallbackI_V() {
    }

    @Alias(names = "static_org_lwjgl_glfw_GLFWXFunctions_V")
    private static void static_org_lwjgl_glfw_GLFWXFunctions_V() {
    }

    @Alias(names = "static_org_lwjgl_glfw_GLFW_V")
    private static void static_org_lwjgl_glfw_GLFW_V() {
    }

    @Alias(names = "static_org_lwjgl_openal_ALCX1_V")
    private static void static_org_lwjgl_openal_ALCX1_V() {
    }

    @Alias(names = "static_org_lwjgl_opengl_GL_V")
    private static void static_org_lwjgl_opengl_GL_V() {
    }

    @Alias(names = "static_org_lwjgl_system_Callback_V")
    private static void static_org_lwjgl_system_Callback_V() {
    }

    @Alias(names = "static_org_lwjgl_system_Checks_V")
    private static void static_org_lwjgl_system_Checks_V() {
    }

    @Alias(names = "static_org_lwjgl_system_Configuration_V")
    private static void static_org_lwjgl_system_Configuration_V() {
    }

    @Alias(names = "static_org_lwjgl_system_MemoryStack_V")
    private static void static_org_lwjgl_system_MemoryStack_V() {
    }

    @Alias(names = "static_org_lwjgl_system_MemoryUtilXLazyInit_V")
    private static void static_org_lwjgl_system_MemoryUtilXLazyInit_V() {
    }

    @Alias(names = "static_org_lwjgl_system_MemoryUtil_V")
    private static void static_org_lwjgl_system_MemoryUtil_V() {
    }

    @Alias(names = "static_org_lwjgl_system_PlatformXArchitecture_V")
    private static void static_org_lwjgl_system_PlatformXArchitecture_V() {
    }

    @Alias(names = "static_org_lwjgl_system_Platform_V")
    private static void static_org_lwjgl_system_Platform_V() {
    }

    @Alias(names = "static_org_lwjgl_system_PointerXDefault_V")
    private static void static_org_lwjgl_system_PointerXDefault_V() {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetErrorCallback_Lorg_lwjgl_glfw_GLFWErrorCallbackILorg_lwjgl_glfw_GLFWErrorCallback")
    private static Object GLFW_glfwSetErrorCallback(Object callback) {
        return null;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetWindowMonitor_JJIIIIIV")
    private static void org_lwjgl_glfw_GLFW_glfwSetWindowMonitor_JJIIIIIV(long a, long b, int x, int y, int w, int h, int o) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwTerminate_V")
    public static void org_lwjgl_glfw_GLFW_glfwTerminate_V() {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwDefaultWindowHints_V")
    public static void org_lwjgl_glfw_GLFW_glfwDefaultWindowHints_V() {
    }

    @NoThrow
    @Alias(names = "new_org_lwjgl_glfw_GLFWErrorCallback_V")
    public static void new_org_lwjgl_glfw_GLFWErrorCallback_V(Object self) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_system_CallbackI_address_J")
    public static long org_lwjgl_system_CallbackI_address_J(Object self) {
        return getAddr(self);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_system_Callback_free_V")
    public static void org_lwjgl_system_Callback_free_V(Object self) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_system_Callback_hashCode_I")
    public static int org_lwjgl_system_Callback_hashCode_I(Object self) {
        return System.identityHashCode(self);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwSetWindowIcon_JLorg_lwjgl_glfw_GLFWImageXBufferV")
    public static void org_lwjgl_glfw_GLFW_glfwSetWindowIcon_JLorg_lwjgl_glfw_GLFWImageXBufferV(GLFWImage.Buffer images) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwDestroyWindow_JV")
    @JavaScript(code = "")
    public static native void org_lwjgl_glfw_GLFW_glfwDestroyWindow_JV(long window);

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwHideWindow_JV")
    @JavaScript(code = "")
    public static native void org_lwjgl_glfw_GLFW_glfwHideWindow_JV(long window);

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwRequestWindowAttention_JV")
    @JavaScript(code = "")
    public static native void org_lwjgl_glfw_GLFW_glfwRequestWindowAttention_JV(long window);

    @NoThrow
    @Alias(names = "org_lwjgl_glfw_GLFW_glfwCreateCursor_Lorg_lwjgl_glfw_GLFWImageIIJ")
    public static long org_lwjgl_glfw_GLFW_glfwCreateCursor_Lorg_lwjgl_glfw_GLFWImageIIJ(GLFWImage image, int cx, int cy) {
        return 0;
    }
}
