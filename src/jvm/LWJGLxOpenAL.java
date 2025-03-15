package jvm;

import annotations.Alias;
import annotations.NoThrow;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.function.IntFunction;

import static jvm.JVM32.getAddr;
import static jvm.JVM32.ptrTo;

public class LWJGLxOpenAL {

    @Alias(names = "org_lwjgl_openal_AL_createCapabilities_Lorg_lwjgl_openal_ALCCapabilitiesLorg_lwjgl_openal_ALCapabilities")
    public static ALCapabilities AL_createCapabilities(ALCCapabilities cap) {
        return null;
    }

    @Alias(names = "org_lwjgl_openal_ALC_createCapabilities_JLjava_util_function_IntFunctionLorg_lwjgl_openal_ALCCapabilities")
    public static ALCapabilities ALC_createCapabilities_JLjava_util_function_IntFunctionLorg_lwjgl_openal_ALCCapabilities(long sth, IntFunction func) {
        return null;
    }

    @Alias(names = "org_lwjgl_openal_ALC_create_Ljava_lang_StringV")
    public static String org_lwjgl_openal_ALC_create_Ljava_lang_StringV() {
        return "ALC";
    }

    @Alias(names = "org_lwjgl_system_MemoryUtil_memUTF8_JLjava_lang_String")
    public static String org_lwjgl_system_MemoryUtil_memUTF8_JLjava_lang_String(long ptr) {
        return ptrTo((int) ptr);
    }

    // not yet supported
    @Alias(names = "org_lwjgl_openal_AL10_alGetError_I")
    public static int org_lwjgl_openal_AL10_alGetError_I() {
        return 0;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL10_alBufferData_IILjava_nio_ShortBufferIV")
    public static void org_lwjgl_openal_AL10_alBufferData_IILjava_nio_ShortBufferIV(int ptr, int b, ShortBuffer data, int d) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL10_alSource3f_IIFFFV")
    public static void org_lwjgl_openal_AL10_alSource3f_IIFFFV(int a, int b, float c, float d, float e) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL10_alGenSources_I")
    public static int org_lwjgl_openal_AL10_alGenSources_I() {
        return 0;
    }


    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL10_alSourcei_IIIV")
    public static void org_lwjgl_openal_AL10_alSourcei_IIIV(int ptr, int key, int value) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL10_alSourcef_IIFV")
    public static void org_lwjgl_openal_AL10_alSourcef_IIFV(int ptr, int key, float value) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL10_alSourcePlay_IV")
    public static void org_lwjgl_openal_AL10_alSourcePlay_IV(int ptr) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL10_alSourceQueueBuffers_IIV")
    public static void org_lwjgl_openal_AL10_alSourceQueueBuffers_IIV(int a, int b) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL10_alSourceStop_IV")
    public static void org_lwjgl_openal_AL10_alSourceStop_IV(int ptr) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_ALC10_alcDestroyContext_JV ")
    public static void org_lwjgl_openal_ALC10_alcDestroyContext_JV(long ptr) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_ALC10_alcMakeContextCurrent_JZ")
    public static boolean org_lwjgl_openal_ALC10_alcMakeContextCurrent_JZ(long ptr) {
        return true;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_ALC10_alcOpenDevice_Ljava_nio_ByteBufferJ")
    public static long org_lwjgl_openal_ALC10_alcOpenDevice_Ljava_nio_ByteBufferJ(ByteBuffer name) {
        return getAddr(name);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL_createCapabilities_Lorg_lwjgl_openal_ALCCapabilitiesLjava_util_IntFunctionLorg_lwjgl_openal_ALCapabilities")
    public static ALCapabilities createCapabilities(ALCCapabilities alcCaps, IntFunction<PointerBuffer> bufferFactory) {
        return null;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL10_alDeleteBuffers_IV")
    public static void org_lwjgl_openal_AL10_alDeleteBuffers_IV(int id) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL10_alDeleteSources_IV")
    public static void org_lwjgl_openal_AL10_alDeleteSources_IV(int id) {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL10_alGetSourcei_III")
    public static int org_lwjgl_openal_AL10_alGetSourcei_III(int i, int j) {
        return 0;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_openal_AL10_alGenBuffers_I")
    public static int org_lwjgl_openal_AL10_alGenBuffers_I() {
        return 1;
    }

}
