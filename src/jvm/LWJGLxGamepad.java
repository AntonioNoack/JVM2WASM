package jvm;

import annotations.Alias;
import annotations.NoThrow;
import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class LWJGLxGamepad {

    @NoThrow
    @Alias(name = "org_lwjgl_glfw_GLFW_glfwGetGamepadState_ILorg_lwjgl_glfw_GLFWGamepadStateZ")
    public static boolean GLFW_glfwGetGamepadState(int id, GLFWGamepadState state) {
        return true;
    }

    @NoThrow
    @Alias(name = "org_lwjgl_glfw_GLFW_glfwGetJoystickButtons_ILjava_nio_ByteBuffer")
    public static ByteBuffer GLFW_glfwGetJoystickButtons(int id) {
        return null;
    }

    @NoThrow
    @Alias(name = "org_lwjgl_glfw_GLFW_glfwGetJoystickAxes_ILjava_nio_FloatBuffer")
    public static FloatBuffer org_lwjgl_glfw_GLFW_glfwGetJoystickAxes_ILjava_nio_FloatBuffer(int id) {
        return null;
    }

    @NoThrow
    @Alias(name = "org_lwjgl_glfw_GLFW_glfwGetJoystickName_ILjava_lang_String")
    public static String org_lwjgl_glfw_GLFW_glfwGetJoystickName_ILjava_lang_String(int id) {
        return null;
    }

    @NoThrow
    @Alias(name = "org_lwjgl_glfw_GLFW_glfwGetJoystickGUID_ILjava_lang_String")
    public static String org_lwjgl_glfw_GLFW_glfwGetJoystickGUID_ILjava_lang_String(int id) {
        return null;
    }

    @NoThrow
    @Alias(name = "org_lwjgl_glfw_GLFW_glfwJoystickIsGamepad_IZ")
    public static boolean org_lwjgl_glfw_GLFW_glfwJoystickIsGamepad_IZ(int id) {
        return false;
    }

    @NoThrow
    @Alias(name = "org_lwjgl_glfw_GLFW_glfwJoystickPresent_IZ")
    public static boolean org_lwjgl_glfw_GLFW_glfwJoystickPresent_IZ(int id) {
        // todo implement using e.g., https://developer.mozilla.org/en-US/docs/Web/API/Gamepad_API/Using_the_Gamepad_API
        return false;
    }

    @Alias(name = "org_lwjgl_glfw_GLFWGamepadState_calloc_Lorg_lwjgl_glfw_GLFWGamepadState")
    public static GLFWGamepadState GLFWGamepadState_calloc() {
        return new GLFWGamepadState(ByteBuffer.allocate(GLFWGamepadState.SIZEOF));
    }

    @Alias(name = "org_lwjgl_glfw_GLFWGamepadState_axes_Ljava_nio_FloatBuffer")
    public static FloatBuffer org_lwjgl_glfw_GLFWGamepadState_axes_Ljava_nio_FloatBuffer(GLFWGamepadState self) {
        return null;
    }

    @Alias(name = "org_lwjgl_glfw_GLFWGamepadState_buttons_Ljava_nio_ByteBuffer")
    public static ByteBuffer org_lwjgl_glfw_GLFWGamepadState_buttons_Ljava_nio_ByteBuffer(GLFWGamepadState self) {
        return null;
    }

}
