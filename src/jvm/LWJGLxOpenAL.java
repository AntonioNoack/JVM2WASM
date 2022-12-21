package jvm;

import annotations.Alias;
import annotations.NoThrow;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;

import java.nio.ShortBuffer;
import java.util.function.IntFunction;

import static jvm.JavaLang.ptrTo;

public class LWJGLxOpenAL {

    @Alias(name = "org_lwjgl_openal_AL_createCapabilities_Lorg_lwjgl_openal_ALCCapabilitiesLorg_lwjgl_openal_ALCapabilities")
    public static ALCapabilities AL_createCapabilities(ALCCapabilities cap) {
        return null;
    }

    @Alias(name = "org_lwjgl_openal_ALC_createCapabilities_JLjava_util_function_IntFunctionLorg_lwjgl_openal_ALCCapabilities")
    public static ALCapabilities ALC_createCapabilities_JLjava_util_function_IntFunctionLorg_lwjgl_openal_ALCCapabilities(long sth, IntFunction func) {
        return null;
    }

    @Alias(name = "org_lwjgl_openal_ALC_create_Ljava_lang_StringV")
    public static String org_lwjgl_openal_ALC_create_Ljava_lang_StringV() {
        return "ALC";
    }

    @Alias(name = "org_lwjgl_system_MemoryUtil_memUTF8_JLjava_lang_String")
    public static String org_lwjgl_system_MemoryUtil_memUTF8_JLjava_lang_String(long ptr) {
        return ptrTo((int) ptr);
    }

    // not yet supported
    @Alias(name = "org_lwjgl_openal_AL10_alGetError_I")
    public static int org_lwjgl_openal_AL10_alGetError_I() {
        return 0;
    }

    @NoThrow
    @Alias(name = "org_lwjgl_openal_AL10_alBufferData_IILjava_nio_ShortBufferIV")
    public static void org_lwjgl_openal_AL10_alBufferData_IILjava_nio_ShortBufferIV(int ptr, int b, ShortBuffer data, int d) {
    }

    @NoThrow
    @Alias(name = "org_lwjgl_openal_AL10_alSource3f_IIFFFV")
    public static void org_lwjgl_openal_AL10_alSource3f_IIFFFV(int a, int b, float c, float d, float e) {
    }

    @NoThrow
    @Alias(name = "org_lwjgl_openal_AL10_alGenSources_I")
    public static int org_lwjgl_openal_AL10_alGenSources_I() {
        return 0;
    }

}
