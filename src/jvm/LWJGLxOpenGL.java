package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import annotations.WASM;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.Callback;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.SharedLibrary;
import sun.misc.Unsafe;

import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.*;
import java.util.function.Consumer;

import static jvm.JVM32.*;
import static jvm.JavaLang.getAddr;
import static org.lwjgl.opengl.GL46C.*;

public class LWJGLxOpenGL {

    @NoThrow
    @Alias(names = "org_lwjgl_system_MemoryAccessJNI_getPointerSize_I")
    @WASM(code = "i32.const 4")
    private static native int getPointerSize();

    @Alias(names = "org_lwjgl_system_MemoryUtil_memAlloc_ILjava_nio_ByteBuffer")
    public static ByteBuffer MemoryUtil_memAlloc(int size) {
        return ByteBuffer.allocate(size);
    }

    @NoThrow
    @Alias(names = {
            "org_lwjgl_opengl_GL46C_glGetError_I",
            "org_lwjgl_opengl_GL46C_glGetError_I",
            "org_lwjgl_opengl_GL46C_glGetError_I"
    })
    @JavaScript(code = "return gl.getError()")
    public static native int GL30C_glGetError_I();

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glCreateProgram_I")
    @JavaScript(code = "return map(gl.createProgram())")
    public static native int glCreateProgram_I();

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glCreateShader_II")
    @JavaScript(code = "return map(gl.createShader(arg0))")
    public static native int glCreateShader_II(int type);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glCompileShader_IV")
    @JavaScript(code = "gl.compileShader(unmap(arg0))")
    public static native void glCompileShader_IV(int shader);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glLinkProgram_IV")
    @JavaScript(code = "gl.linkProgram(unmap(arg0))")
    public static native void glLinkProgram_IV(int program);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glAttachShader_IIV")
    @JavaScript(code = "gl.attachShader(unmap(arg0), unmap(arg1))")
    public static native void glAttachShader_IIV(int program, int shader);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glUseProgram_IV")
    @JavaScript(code = "gl.useProgram(unmap(arg0))")
    public static native void glUseProgram_IV(int program);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glValidateProgram_IV")
    @JavaScript(code = "gl.validateProgram(unmap(arg0))")
    public static native void glValidateProgram_IV(int program);

    @NoThrow
    @JavaScript(code = "gl.uniform1f(unmap(arg0),arg1)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniform1f_IFV")
    public static native void glUniform1f(int location, float x);

    @NoThrow
    @JavaScript(code = "gl.uniform1i(unmap(arg0),arg1)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniform1i_IIV")
    public static native void glUniform1i(int location, int x);

    @NoThrow
    @JavaScript(code = "gl.uniform2f(unmap(arg0),arg1,arg2)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniform2f_IFFV")
    public static native void glUniform2f(int location, float x, float y);

    @NoThrow
    @JavaScript(code = "gl.uniform2i(unmap(arg0),arg1,arg2)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniform2i_IIIV")
    public static native void glUniform2i(int location, int x, int y);

    @NoThrow
    @JavaScript(code = "gl.uniform3f(unmap(arg0),arg1,arg2,arg3)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniform3f_IFFFV")
    public static native void glUniform3f(int location, float x, float y, float z);

    @NoThrow
    @JavaScript(code = "gl.uniform3i(unmap(arg0),arg1,arg2,arg3)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniform3i_IIIIV")
    public static native void glUniform3i(int location, int x, int y, int z);

    @NoThrow
    @JavaScript(code = "gl.uniform4f(unmap(arg0),arg1,arg2,arg3,arg4)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniform4f_IFFFFV")
    public static native void glUniform4f(int location, float x, float y, float z, float w);

    @NoThrow
    @JavaScript(code = "gl.uniform4i(unmap(arg0),arg1,arg2,arg3,arg4)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniform4i_IIIIIV")
    public static native void glUniform4i(int location, int x, int y, int z, int w);

    @NoThrow // 34895 = GL_TEXTURE_CUBE_MAP_SEAMLESS, 2848 = GL_LINE_SMOOTH, 32925 = GL_MULTISAMPLE, not supported
    @JavaScript(code = "if(arg0 != 34895 && arg0 != 2848 && arg0 != 32925) gl.enable(arg0)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glEnable_IV")
    public static native void glEnable(int mode);

    @NoThrow
    @JavaScript(code = "if(arg0 != 34895 && arg0 != 2848 && arg0 != 32925) gl.disable(arg0)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glDisable_IV")
    public static native void glDisable(int mode);

    @NoThrow
    @JavaScript(code = "return map(gl.getUniformLocation(unmap(arg0), str(arg1)))")
    public static native int glGetUniformLocationString(int program, String name);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glGetUniformLocation_ILjava_lang_CharSequenceI")
    public static int glGetUniformLocation(int program, CharSequence name) {
        return glGetUniformLocationString(program, name.toString());
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glGenFramebuffers_I")
    @JavaScript(code = "return map(gl.createFramebuffer())")
    public static native int glGenFramebuffers_I();

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glGenRenderbuffers_I")
    @JavaScript(code = "return map(gl.createRenderbuffer())")
    public static native int glGenRenderbuffers_I();

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glBindFramebuffer_IIV")
    @JavaScript(code = "gl.bindFramebuffer(arg0,unmap(arg1))")
    public static native void glBindFramebuffer_IIV(int type, int ptr);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glBindRenderbuffer_IIV")
    @JavaScript(code = "gl.bindRenderbuffer(arg0,unmap(arg1))")
    public static native void glBindRenderbuffer_IIV(int type, int ptr);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glBlitFramebuffer_IIIIIIIIIIV")
    @JavaScript(code = "gl.blitFramebuffer(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8,arg9)")
    public static native void glBlitFramebuffer_10xI_V(int a, int b, int c, int d, int e,
                                                       int f, int g, int h, int i, int j);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glViewport_IIIIV")
    @JavaScript(code = "gl.viewport(arg0,arg1,arg2,arg3)")
    public static native void glViewport_IIIIV(int x, int y, int w, int h);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glBlendEquationSeparate_IIV")
    @JavaScript(code = "gl.blendEquationSeparate(arg0,arg1)")
    public static native void glBlendEquationSeparate_IIV(int a, int b);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glBlendFuncSeparate_IIIIV")
    @JavaScript(code = "gl.blendFuncSeparate(arg0,arg1,arg2,arg3)")
    public static native void glBlendFuncSeparate_IIIIV(int a, int b, int c, int d);

    @NoThrow
    @JavaScript(code = "return map(gl.createTexture())")
    public static native int glGenTexture();

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glGenTextures_AIV")
    public static void org_lwjgl_opengl_GL11C_glGenTextures_AIV(int[] v) {
        for (int i = 0, l = v.length; i < l; i++) {
            v[i] = glGenTexture();
        }
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glClearColor_FFFFV")
    @JavaScript(code = "gl.clearColor(arg0,arg1,arg2,arg3)")
    public static native void org_lwjgl_opengl_GL11C_glClearColor_FFFFV(float r, float g, float b, float a);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glClearDepth_DV")
    @JavaScript(code = "gl.clearDepth(arg0)")
    public static native void org_lwjgl_opengl_GL11C_glClearDepth_DV(double depth);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glClear_IV")
    @JavaScript(code = "gl.clear(arg0)")
    public static native void org_lwjgl_opengl_GL11C_glClear_IV(int mask);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDeleteProgram_IV")
    @JavaScript(code = "gl.deleteProgram(unmap(arg0))")
    public static native void glDeleteProgram_IV(int program);

    // if statement for WebGL, because it doesn't have GL_TEXTURE_2D_MULTISAMPLE
    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glBindTexture_IIV")
    @JavaScript(code = "if(arg0 == 37120) arg0 = 3553; gl.bindTexture(arg0,unmap(arg1))")
    public static native void org_lwjgl_opengl_GL45C_glBindTexture_IIV(int mode, int tex);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glActiveTexture_IV")
    @JavaScript(code = "gl.activeTexture(arg0)")
    public static native void org_lwjgl_opengl_GL45C_glActiveTexture_IV(int id);

    // todo implement multisampling like https://stackoverflow.com/questions/47934444/webgl-framebuffer-multisampling
    // https://webglfundamentals.org/webgl/lessons/webgl-shaders-and-glsl.html
    // https://hacks.mozilla.org/2014/01/webgl-deferred-shading/
    // this doesn't use a texture, but a render buffer; as multi-sampled texture don't seem to exist in WebGL
    /*@NoThrow
    @Alias(name = "org_lwjgl_opengl_GL46C_glTexImage2DMultisample_IIIIIZV")
    @JavaScript(code = "gl.texImage2D()")
    public static native void org_lwjgl_opengl_GL45C_glTexImage2DMultisample_IIIIIZV(int target, int samples, int format, int w, int h, boolean fixedSampleLocations);
*/
    @Alias(names = "static_org_lwjgl_system_Struct_V")
    public static void Struct_clinit() {
        // crashes, because it doesn't find the correct field for reflections
        // however we don't need reflections in LWJGL, so I don't really care
    }

    @NoThrow
    @Alias(names = "org_lwjgl_system_MemoryUtil_memAddress_Ljava_nio_ByteBufferJ")
    public static long MemoryUtil_memAddress(ByteBuffer buffer) {
        return getAddr(buffer);
    }

    private static SharedLibrary lib;

    @NoThrow
    @Alias(names = "org_lwjgl_system_Library_loadNative_Ljava_lang_ClassLjava_lang_StringLjava_lang_StringZZLorg_lwjgl_system_SharedLibrary")
    public static SharedLibrary Library_loadNative(Class<?> context, String module, String name, boolean bundledWithLWJGL, boolean printError) {
        if (lib == null) lib = new SharedLibrary() {

            @Override
            public long address() {
                return 0;
            }

            @Override
            public void free() {
            }

            @Override
            public long getFunctionAddress(@NotNull ByteBuffer byteBuffer) {
                return 0;
            }

            @NotNull
            @Override
            public String getName() {
                return "";
            }

            @Override
            public String getPath() {
                return "";
            }
        };
        return lib;
    }

    @Alias(names = "org_lwjgl_system_FunctionProvider_getFunctionAddress_Ljava_lang_CharSequenceJ")
    public static long FunctionProvider_getFunctionAddress(FunctionProvider provider, CharSequence name) {
        return getAddr(name);
    }

    @Alias(names = "org_lwjgl_system_MemoryStack_mallocInt_ILjava_nio_IntBuffer")
    public static IntBuffer mallocInt(MemoryStack self, int size) {
        return IntBuffer.allocate(size);
    }

    @Alias(names = "org_lwjgl_system_MemoryUtil_getUnsafeInstance_Lsun_misc_Unsafe")
    public static Unsafe org_lwjgl_system_MemoryUtil_getUnsafeInstance_Lsun_misc_Unsafe() {
        return Unsafe.getUnsafe();
    }

    @Alias(names = "org_lwjgl_system_MemoryUtil_memAddress_Ljava_nio_IntBufferJ")
    public static long org_lwjgl_system_MemoryUtil_memAddress_Ljava_nio_IntBufferJ(IntBuffer buffer) {
        return getAddr(buffer);
    }

    @Alias(names = "org_lwjgl_system_Library_loadSystem_Ljava_util_function_ConsumerLjava_util_function_ConsumerLjava_lang_ClassLjava_lang_StringLjava_lang_StringV")
    public static <V> void org_lwjgl_system_Library_loadSystem(Consumer<V> x, Consumer<V> y, Class<V> ctx, String module, String name) {
    }

    @Alias(names = "org_lwjgl_opengl_GL_create_V")
    public static void org_lwjgl_opengl_GL_create_V() {
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glShaderSource_ILjava_lang_CharSequenceV")
    @JavaScript(code = "gl.shaderSource(unmap(arg0),str(arg1)" +
            ".split('#extension').join('// #ext')" +
            ".split('gl_SampleID').join('0')" + // todo why is this not supported???
            ")")
    public static native void glShaderSource_ILjava_lang_CharSequenceV(int shader, CharSequence source);

    @NoThrow
    @JavaScript(code = "return fill(arg0, gl.getShaderInfoLog(unmap(arg1)))")
    public static native int fillShaderInfoLog(char[] data, int shader);

    @NoThrow
    @JavaScript(code = "return fill(arg0, gl.getProgramInfoLog(unmap(arg1)))")
    public static native int fillProgramInfoLog(char[] data, int program);


    @Alias(names = "org_lwjgl_opengl_GL46C_glGetShaderInfoLog_ILjava_lang_String")
    public static String GL20_glGetShaderInfoLog(int shader) {
        char[] buffer = FillBuffer.getBuffer();
        int length = fillShaderInfoLog(buffer, shader);
        return filterErrors(buffer, length);
    }

    @Alias(names = "org_lwjgl_opengl_GL46C_glGetProgramInfoLog_ILjava_lang_String")
    public static String GL20_glGetProgramInfoLog(int program) {
        char[] buffer = FillBuffer.getBuffer();
        int length = fillProgramInfoLog(buffer, program);
        return filterErrors(buffer, length);
    }

    private static String filterErrors(char[] buffer, int length) {
        if (length == 0) return null;
        String s = new String(buffer, 0, length);
        // check each line of the message
        String[] lines = split(s, '\n');
        StringBuilder result = new StringBuilder(s.length());
        for (String line : lines) {
            if (!line.isEmpty() && needsToPrintError(line)) {
                if (result.length() > 0) {
                    result.append('\n');
                }
                result.append(line);
            }
        }
        return result.length() > 0 ? result.toString() : null;
    }

    private static int count(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            count += (s.charAt(i) == c) ? 1 : 0;
        }
        return count;
    }

    @SuppressWarnings("SameParameterValue")
    private static String[] split(String s, char separator) {
        String[] result = new String[count(s, separator) + 1];
        int i = 0, k = 0;
        while (true) {
            int j = s.indexOf(separator, i);
            if (j < 0) break;
            result[k++] = s.substring(i, j);
            i = j + 1;
        }
        result[k] = s.substring(i);
        return result;
    }

    private static boolean needsToPrintError(String s) {
        if (s.contains("warning")) {
            log(s);
            return false;
        } else return true;
    }

    @NoThrow
    @JavaScript(code = "return gl.getProgramParameter(unmap(arg0),arg1)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glGetProgrami_III")
    public static native int glGetProgrami_III(int program, int type);

    @Alias(names = "org_lwjgl_opengl_GL_createCapabilities_Lorg_lwjgl_opengl_GLCapabilities")
    public static GLCapabilities org_lwjgl_opengl_GL_createCapabilities() {
        return null;
    }

    @Alias(names = "org_lwjgl_opengl_GLUtil_setupDebugMessageCallback_Ljava_io_PrintStreamLorg_lwjgl_system_Callback")
    public static Callback setupDebugMessageCallback(PrintStream stream) {
        return null;
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glGenerateMipmap_IV")
    @JavaScript(code = "gl.generateMipmap(arg0)")
    public static native void glGenerateMipmap_IV(int target);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glGetInteger_II")
    @JavaScript(code = "" +
            "if(arg0 == 0x8D57) return 1;\n" + // multisampled texture aren't supported, multisampled renderbuffers would be
            "if(arg0 == 0x826E) return 1024;\n" + // max uniform locations; not defined in WebGL
            "if(arg0 == 0x821B || arg0 == 0x821C) return 0;\n" + // 0x821B, 0x821C = major, minor version
            "if(arg0 == 0x8CDF) return 1;\n" + // max color attachments
            "return gl.getParameter(arg0)")
    public static native int glGetIntegerImpl(int i);

    @Alias(names = "org_lwjgl_opengl_GL46C_glGetString_ILjava_lang_String")
    public static String org_lwjgl_opengl_GL11C_glGetString_ILjava_lang_String(int mode) {
        switch (mode) {
            case GL_VERSION:
                return "WebGL2"; // todo return webgl if webgl1
            case GL_SHADING_LANGUAGE_VERSION:
                return "1.0";// todo find this out
            default:
                return null;
        }
    }

    @NoThrow
    @JavaScript(code = "/*console.log('glTexImage2D', arguments);*/gl.texImage2D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,null)")
    // null is needed why-ever...
    private static native void texImage2DNullptr(int target, int level, int format, int w, int h, int border, int dataFormat, int dataType);

    @NoThrow
    @JavaScript(code = "/*console.log('glTexImage3D', arguments);*/gl.texImage3D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8,null)")
    // null is needed why-ever...
    private static native void texImage3DNullptr(int target, int level, int format, int w, int h, int d, int border, int dataFormat, int dataType);

    @NoThrow
    @JavaScript(code = "" +
            "/*console.log('glTexImage2D', arguments);*/\n" +
            "gl.texImage2D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7," +
            "   arg7 == gl.UNSIGNED_INT ?" +
            "   new Uint32Array(memory.buffer, arg8, arg9>>2):" +
            "   arg7 == gl.FLOAT ?" +
            "   new Float32Array(memory.buffer, arg8, arg9>>2):" +
            "   arg7 == 0x140B ?" + // half float -> short
            "   new Uint16Array(memory.buffer, arg8, arg9>>1):" +
            "   new Uint8Array(memory.buffer, arg8, arg9))")
    private static native void texImage2DAny(int target, int level, int format, int w, int h, int border, int dataFormat, int dataType, int ptr, int length);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glTexImage2D_IIIIIIIIAFV")
    private static void glTexImage2D_IIIIIIIIAFV(
            int target, int level, int format, int w,
            int h, int border, int dataFormat, int dataType, float[] data) {
        texImage2DAny(target, level, format, w, h, border, dataFormat, dataType, getAddr(data) + arrayOverhead, data.length << 2);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIILjava_nio_IntBufferV")
    private static void glTexImage2D_IIIIIIIILjava_nio_IntBufferV(
            int target, int level, int format, int w,
            int h, int border, int dataFormat, int dataType, IntBuffer data) throws NoSuchFieldException, IllegalAccessException {
        texImage2DAny(target, level, format, w, h, border, dataFormat, dataType, getBufferAddr(data), data.remaining() << 2);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIIASV")
    private static void glTexImage2D_IIIIIIIIASV(
            int a, int b, int c, int d, int e, int f, int g, int h, short[] data) {
        texImage2DAny(a, b, c, d, e, f, g, h, getAddr(data) + arrayOverhead, data.length);
    }

    @NoThrow
    @JavaScript(code = "" +
            "console.log('glTexImage3D', arguments);\n" +
            "gl.texImage3D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8," +
            "   arg8 == gl.UNSIGNED_INT ?" +
            "   new Uint32Array(memory.buffer, arg9, arg10>>2):" +
            "   arg8 == gl.FLOAT ?" +
            "   new Float32Array(memory.buffer, arg9, arg10>>2):" +
            "   new Uint8Array(memory.buffer, arg9, arg10))")
    private static native void texImage3DAny(int target, int level, int format, int w, int h, int d, int border, int dataFormat, int dataType, int ptr, int length);

    @NoThrow
    @JavaScript(code = "" +
            // fps panel is ignored for now
            "/*if(arg4 != 250 || arg5 != 1) console.log('glTexSubImage2D', arguments);*/\n" +
            "gl.texSubImage2D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7," +
            "   arg7 == gl.UNSIGNED_INT ?" +
            "   new Uint32Array(memory.buffer, arg8, arg9>>2):" +
            "   arg7 == gl.FLOAT ?" +
            "   new Float32Array(memory.buffer, arg8, arg9>>2):" +
            "   arg7 == 0x140B ?" + // half float -> short
            "   new Uint16Array(memory.buffer, arg8, arg9>>1):" +
            "   new Uint8Array(memory.buffer, arg8, arg9))")
    private static native void texSubImage2D(int target, int level, int x, int y, int w, int h, int dataFormat, int dataType, int ptr, int length);

    @Alias(names = "org_lwjgl_opengl_GL46C_glTexSubImage2D_IIIIIIIIAIV")
    public static void glTexSubImage2D_IIIIIIIIAIV(
            int target, int level, int x, int y,
            int w, int h, int dataFormat, int dataType, int[] data) {
        boolean swizzle = dataFormat == GL_BGRA;
        if (swizzle) dataFormat = GL_RGBA;
        if (swizzle) rgba2argb(data);
        texSubImage2D(target, level, x, y, w, h, dataFormat, dataType, getAddr(data) + arrayOverhead, data.length << 2);
        if (swizzle) argb2rgba(data);
    }

    @NoThrow
    @JavaScript(code = "gl.scissor(arg0,arg1,arg2,arg3)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glScissor_IIIIV")
    private static native void GL11C_glScissor_IIIIV(int x, int y, int w, int h);

    @NoThrow
    @JavaScript(code = "gl.flush()")
    @Alias(names = "org_lwjgl_opengl_GL46C_glFlush_V")
    private static native void GL11C_glFlush_V();

    @NoThrow
    @JavaScript(code = "gl.finish()")
    @Alias(names = "org_lwjgl_opengl_GL46C_glFinish_V")
    private static native void GL11C_glFinish_V();

    @NoThrow
    @JavaScript(code = "gl.readPixels(arg0,arg1,arg2,arg3,arg4,arg5,new Uint8Array(memory.buffer,arg6,arg7))")
    public static native void readPixelsU8(int x, int y, int w, int h, int format, int type, int data, int length);

    @Alias(names = "org_lwjgl_opengl_GL46C_glReadPixels_IIIIIIAIV")
    public static void glReadPixels_IIIIIIAIV(int x, int y, int w, int h, int format, int type, int[] data) {
        readPixelsU8(x, y, w, h, format, type, getAddr(data) + arrayOverhead, data.length << 2);
    }

    @NoThrow
    @JavaScript(code = "gl.readPixels(arg0,arg1,arg2,arg3,arg4,arg5,new Float32Array(memory.buffer,arg6,arg7))")
    public static native void readPixelsF32(int x, int y, int w, int h, int format, int type, int dataPtr, int length);

    @Alias(names = "org_lwjgl_opengl_GL46C_glReadPixels_IIIIIIAFV")
    public static void glReadPixels_IIIIIIAFV(int x, int y, int w, int h, int format, int type, float[] data) {
        readPixelsF32(x, y, w, h, format, type, getAddr(data) + arrayOverhead, data.length);
    }

    @NoThrow // todo for JavaScript, bind texture to framebuffer, like we did it previously, and then use glReadPixels
    @JavaScript(code = "/* not yet implemented */")
    public static native void glGetTextureSubImageAny(
            int ptr, int level, int x, int y, int z,
            int w, int h, int d, int format, int type, int dataPtr, int length
    );

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL45C_glGetTextureSubImage_IIIIIIIIIIAFV")
    public static void glGetTextureSubImage_IIIIIIIIIIAFV(
            int ptr, int level, int x, int y, int z,
            int w, int h, int d, int format, int type, float[] dst
    ) {
        glGetTextureSubImageAny(ptr, level, x, y, z, w, h, d, format, type,
                getAddr(dst) + arrayOverhead, dst.length << 2);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL45C_glGetTextureSubImage_IIIIIIIIIIAIV")
    public static void glGetTextureSubImage_IIIIIIIIIIAIV(
            int ptr, int level, int x, int y, int z,
            int w, int h, int d, int format, int type, int[] dst
    ) {
        glGetTextureSubImageAny(ptr, level, x, y, z, w, h, d, format, type,
                getAddr(dst) + arrayOverhead, dst.length << 2);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL45C_glGetTextureSubImage_IIIIIIIIIILjava_nio_ByteBufferV")
    public static void glGetTextureSubImage_IIIIIIIIIILjava_nio_ByteBufferV(
            int ptr, int level, int x, int y, int z,
            int w, int h, int d, int format, int type, ByteBuffer dst
    ) throws NoSuchFieldException, IllegalAccessException {
        glGetTextureSubImageAny(ptr, level, x, y, z, w, h, d, format, type,
                getBufferAddr(dst), dst.remaining());
    }

    @Alias(names = "org_lwjgl_opengl_GL46C_glTexImage2D_IIIIIIIILjava_nio_ByteBufferV")
    public static void glTexImage2D_IIIIIIIILjava_nio_ByteBufferV(
            int target, int level, int format, int w, int h, int border,
            int dataFormat, int dataType, ByteBuffer data
    ) throws NoSuchFieldException, IllegalAccessException {
        // WebGL1
        //texImage2D(target, level, internalformat, width, height, border, format, type)
        //texImage2D(target, level, internalformat, width, height, border, format, type, pixels) // pixels a TypedArray or a DataView
        //texImage2D(target, level, internalformat, format, type)
        //texImage2D(target, level, internalformat, format, type, pixels)
        //
        //// WebGL2
        //texImage2D(target, level, internalformat, width, height, border, format, type, offset)
        //texImage2D(target, level, internalformat, width, height, border, format, type, source)
        //texImage2D(target, level, internalformat, width, height, border, format, type, srcData, srcOffset)
        if (data == null) {
            texImage2DNullptr(target, level, format, w, h, border, dataFormat, dataType);
        } else {
            texImage2DAny(target, level, format, w, h, border, dataFormat, dataType, getBufferAddr(data), data.remaining());
        }
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glTexImage2D_IIIIIIIIAIV")
    public static void org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIIAIV(
            int target, int level, int format, int w, int h, int border,
            int dataFormat, int dataType, int[] data) {
        boolean swizzle = dataFormat == GL_BGRA;
        if (swizzle) dataFormat = GL_RGBA;
        if (data == null) {
            texImage2DNullptr(target, level, format, w, h, border, dataFormat, dataType);
        } else {
            if (swizzle) rgba2argb(data);
            texImage2DAny(target, level, format, w, h, border, dataFormat, dataType, getAddr(data) + arrayOverhead, data.length << 2);
            if (swizzle) argb2rgba(data);
        }
    }

    @Alias(names = "org_lwjgl_opengl_GL46C_glTexImage2D_IIIIIIIILjava_nio_FloatBufferV")
    public static void org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIILjava_nio_FloatBufferV(
            int target, int level, int format, int w, int h, int border,
            int dataFormat, int dataType, FloatBuffer data
    ) throws NoSuchFieldException, IllegalAccessException {
        if (data == null) {
            texImage2DNullptr(target, level, format, w, h, border, dataFormat, dataType);
        } else {
            texImage2DAny(target, level, format, w, h, border, dataFormat, dataType,
                    getBufferAddr(data), data.remaining() << 2);
        }
    }

    @Alias(names = "org_lwjgl_opengl_GL46C_glTexImage2D_IIIIIIIILjava_nio_ShortBufferV")
    public static void org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIILjava_nio_ShortBufferV(
            int target, int level, int format, int w, int h, int border,
            int dataFormat, int dataType, ShortBuffer data
    ) throws NoSuchFieldException, IllegalAccessException {
        if (data == null) {
            texImage2DNullptr(target, level, format, w, h, border, dataFormat, dataType);
        } else {
            texImage2DAny(target, level, format, w, h, border, dataFormat, dataType, getBufferAddr(data), data.remaining() << 1);
        }
    }

    @Alias(names = "org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIILjava_nio_ByteBufferV")
    public static void org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIILjava_nio_ByteBufferV(
            int target, int level, int format, int w, int h, int border,
            int dataFormat, int dataType, ByteBuffer data
    ) throws NoSuchFieldException, IllegalAccessException {
        texSubImage2D(target, level, format, w, h, border, dataFormat, dataType, getBufferAddr(data), data.remaining());
    }

    @Alias(names = "org_lwjgl_opengl_GL46C_glTexSubImage2D_IIIIIIIILjava_nio_FloatBufferV")
    public static void org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIILjava_nio_FloatBufferV(
            int target, int level, int format, int w, int h, int border,
            int dataFormat, int dataType, FloatBuffer data
    ) throws NoSuchFieldException, IllegalAccessException {
        texSubImage2D(target, level, format, w, h, border, dataFormat, dataType, getBufferAddr(data), data.remaining() << 2);
    }

    @Alias(names = "org_lwjgl_opengl_GL46C_glTexSubImage2D_IIIIIIIILjava_nio_ShortBufferV")
    public static void org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIILjava_nio_ShortBufferV(
            int target, int level, int format, int w, int h, int border,
            int dataFormat, int dataType, ShortBuffer data
    ) throws NoSuchFieldException, IllegalAccessException {
        texSubImage2D(target, level, format, w, h, border, dataFormat, dataType, getBufferAddr(data), data.remaining() << 1);
    }

    @Alias(names = "org_lwjgl_opengl_GL12C_glTexImage3D_IIIIIIIIILjava_nio_ByteBufferV")
    public static void org_lwjgl_opengl_GL12C_glTexImage3D_IIIIIIIIILjava_nio_ByteBufferV(
            int target, int level, int format, int w, int h, int d, int border,
            int dataFormat, int dataType, ByteBuffer data) throws NoSuchFieldException, IllegalAccessException {
        if (data == null) {
            texImage3DNullptr(target, level, format, w, h, d, border, dataFormat, dataType);
        } else {
            texImage3DAny(target, level, format, w, h, d, border, dataFormat, dataType, getBufferAddr(data), data.remaining());
        }
    }

    @NoThrow
    @WASM(code = "i32.const 8 i32.rotl")
    public static native int argb2rgba(int data);

    @NoThrow
    @WASM(code = "i32.const 8 i32.rotr")
    public static native int rgba2argb(int data);

    @NoThrow
    @SuppressWarnings("CommentedOutCode")
    public static void argb2rgba(int[] data) {
        int addr = getAddr(data) + arrayOverhead;
        int end = addr + (data.length << 2);
        while (unsignedLessThan(addr, end)) {
            write32(addr, argb2rgba(read32(addr)));
            addr += 4;
        }
        /*for (int i = 0, l = data.length; i < l; i++) {
            int di = data[i];
            data[i] = (di << 8) | (di >>> 24);
        }*/
    }

    @NoThrow
    @SuppressWarnings("CommentedOutCode")
    public static void rgba2argb(int[] data) {
        int addr = getAddr(data) + arrayOverhead;
        int end = addr + (data.length << 2);
        while (unsignedLessThan(addr, end)) {
            write32(addr, rgba2argb(read32(addr)));
            addr += 4;
        }
        /*for (int i = 0, l = data.length; i < l; i++) {
            int di = data[i];
            data[i] = (di >>> 8) | (di << 24);
        }*/
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glTexParameteriv_IIAIV")
    // @JavaScript(code = "gl.texParameteriv(arg0,arg1,new Uint32Array(memory.buffer,arg2+arrayOverhead,lib.r32(arg2+objectOverhead)))")
    public static void org_lwjgl_opengl_GL11C_glTexParameteriv_IIAIV(int target, int mode, int[] value) {
        if (mode == 0x8E46) log("Warning(glTexParameteriv): texture swizzling is not supported in WebGL");
        else log("Warning(glTexParameteriv)! Unknown mode", mode);
    }

    private static int getBufferAddr(Buffer data, int shift) throws NoSuchFieldException, IllegalAccessException {
        Class<?> clazz = data.getClass();
        // ByteBuffer.allocate(10).asFloatBuffer();
        Object nativeArray = clazz.getField("hb").get(data);
        int offset0 = arrayOverhead + (data.position() << shift);
        // java.nio.HeapFloatBuffer, is created from FloatBuffer.allocate()
        if (nativeArray != null) return getAddr(nativeArray) + offset0;
        // java.nio.ByteBufferAsFloatBufferL, is created from asFloatBuffer()
        ByteBuffer data2 = (ByteBuffer) clazz.getField("bb").get(data);
        byte[] bytes = data2.array();
        int offset = clazz.getField("offset").getInt(data);
        return getAddr(bytes) + offset + offset0;
    }

    private static int getBufferAddr(ByteBuffer data) throws NoSuchFieldException, IllegalAccessException {
        return getBufferAddr(data, 1);
    }

    private static int getBufferAddr(FloatBuffer data) throws NoSuchFieldException, IllegalAccessException {
        return getBufferAddr(data, 2);
    }

    private static int getBufferAddr(ShortBuffer data) throws NoSuchFieldException, IllegalAccessException {
        return getBufferAddr(data, 1);
    }

    private static int getBufferAddr(IntBuffer data) throws NoSuchFieldException, IllegalAccessException {
        return getBufferAddr(data, 2);
    }

    private static int getBufferAddr(DoubleBuffer data) throws NoSuchFieldException, IllegalAccessException {
        return getBufferAddr(data, 3);
    }

    @NoThrow
    @JavaScript(code = "gl.uniform1fv(unmap(arg0), new Float32Array(memory.buffer, arg1, arg2))")
    public static native void glUniform1fv(int location, int addr, int length);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniform1fv_ILjava_nio_FloatBufferV")
    public static void glUniform1fv_ILjava_nio_FloatBufferV(int location, FloatBuffer data) throws NoSuchFieldException, IllegalAccessException {
        glUniform1fv(location, getBufferAddr(data), data.remaining());
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniform1fv_IAFV")
    public static void glUniform1fv_IAFV(int location, float[] data) {
        glUniform1fv(location, getAddr(data) + arrayOverhead, data.length);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glGetIntegeri_v_IIAIV")
    @JavaScript(code = "throw 'Not supported/implemented'")
    public static native void glGetIntegeri_v_IIAIV(int query, int index, int[] dst);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL15C_glDeleteQueries_AIV")
    public static void org_lwjgl_opengl_GL15C_glDeleteQueries_AIV(int[] queries) {
        for (int id : queries) glDeleteQueries(id);
    }

    @NoThrow
    @JavaScript(code = "gl.uniform4fv(unmap(arg0), new Float32Array(memory.buffer, arg1, arg2))")
    public static native void glUniform4fv(int location, int addr, int length);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniform4fv_ILjava_nio_FloatBufferV")
    public static void glUniform4fv(int location, FloatBuffer data) throws NoSuchFieldException, IllegalAccessException {
        glUniform4fv(location, getBufferAddr(data), data.remaining());
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniform4fv_IAFV")
    public static void glUniform4fv(int location, float[] data) {
        glUniform4fv(location, getAddr(data) + arrayOverhead, data.length);
    }

    @NoThrow
    @JavaScript(code = "gl.uniformMatrix4x3fv(unmap(arg0), arg1, new Float32Array(memory.buffer, arg2, arg3))")
    public static native void uniformMatrix4x3fv(int location, boolean transpose, int addr, int length);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniformMatrix4x3fv_IZLjava_nio_FloatBufferV")
    public static void GL21C_glUniformMatrix4x3fv(int location, boolean transpose, FloatBuffer data) throws NoSuchFieldException, IllegalAccessException {
        uniformMatrix4x3fv(location, transpose, getBufferAddr(data), data.remaining());
    }

    @NoThrow
    @JavaScript(code = "gl.uniformMatrix4fv(unmap(arg0), arg1, new Float32Array(memory.buffer, arg2, arg3))")
    public static native void uniformMatrix4fv(int location, boolean transpose, int addr, int length);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glUniformMatrix4fv_IZLjava_nio_FloatBufferV")
    public static void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer data) throws NoSuchFieldException, IllegalAccessException {
        uniformMatrix4fv(location, transpose, getBufferAddr(data), data.remaining());
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL15C_glGenBuffers_I")
    @JavaScript(code = "return map(gl.createBuffer())")
    public static native int GL15C_glGenBuffers_I();

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL15C_glBindBuffer_IIV")
    @JavaScript(code = "gl.bindBuffer(arg0,unmap(arg1))")
    public static native void GL15C_glBindBuffer_IIV(int target, int buffer);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL15C_glDeleteBuffers_IV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL15C_glDeleteBuffers_IV'")
    public static native void GL15C_glDeleteBuffers_IV(int buffer);

    @NoThrow
    @JavaScript(code = "gl.uniformMatrix2fv(unmap(arg0), arg1, new Float32Array(memory.buffer, arg2, arg3))")
    private static native void uniformMatrix2fv(int u, boolean t, int data, int length);

    @NoThrow
    @JavaScript(code = "gl.uniformMatrix3fv(unmap(arg0), arg1, new Float32Array(memory.buffer, arg2, arg3))")
    private static native void uniformMatrix3fv(int u, boolean t, int data, int length);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL20C_glUniformMatrix2fv_IZLjava_nio_FloatBufferV")
    public static void glUniformMatrix2fv_IZLjava_nio_FloatBufferV(int u, boolean t, FloatBuffer data)
            throws NoSuchFieldException, IllegalAccessException {
        uniformMatrix2fv(u, t, getBufferAddr(data), data.remaining());
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL20C_glUniformMatrix3fv_IZLjava_nio_FloatBufferV")
    public static void glUniformMatrix3fv_IZLjava_nio_FloatBufferV(int u, boolean t, FloatBuffer data)
            throws NoSuchFieldException, IllegalAccessException {
        uniformMatrix3fv(u, t, getBufferAddr(data), data.remaining());
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL11C_glReadPixels_IIIIIILjava_nio_ByteBufferV")
    public static void org_lwjgl_opengl_GL11C_glReadPixels_IIIIIILjava_nio_ByteBufferV(
            int x, int y, int w, int h, int a, int b, ByteBuffer data
    ) throws NoSuchFieldException, IllegalAccessException {
        readPixelsU8(x, y, w, h, a, b, getBufferAddr(data), data.remaining());
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIILjava_nio_DoubleBufferV")
    public static void org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIILjava_nio_DoubleBufferV(
            int a, int b, int c, int d, int e, int f, int g, int h, DoubleBuffer data
    ) throws NoSuchFieldException, IllegalAccessException {
        if (data == null) {
            texImage2DNullptr(a, b, c, d, e, f, g, h);
        } else {
            texImage2DAny(a, b, c, d, e, f, g, h, getBufferAddr(data), data.remaining() << 3);
        }
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL43C_glDebugMessageCallback_Lorg_lwjgl_opengl_GLDebugMessageCallbackIJV")
    public static void glDebugMessageCallback_Lorg_lwjgl_opengl_GLDebugMessageCallbackIJV(Object callback, long j) {
        log("Setting debugCallback isn't supported");
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL15C_glBufferSubData_IJLjava_nio_ShortBufferV")
    public static void glBufferSubData_IJLjava_nio_ShortBufferV(int i, long offset, ShortBuffer data)
            throws NoSuchFieldException, IllegalAccessException {
        bufferSubData16(i, offset, getBufferAddr(data), data.remaining());
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIILjava_nio_IntBufferV")
    public static void glTexSubImage2D_IIIIIIIILjava_nio_IntBufferV(
            int a, int b, int c, int d, int e, int f, int g, int h, IntBuffer data
    ) throws NoSuchFieldException, IllegalAccessException {
        texSubImage2D(a, b, c, d, e, f, g, h, getBufferAddr(data), data.remaining() << 2);
    }

    @Alias(names = "org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIILjava_nio_DoubleBufferV")
    public static void glTexSubImage2D_IIIIIIIILjava_nio_DoubleBufferV(
            int a, int b, int c, int d, int e, int f, int g, int h, DoubleBuffer data
    ) throws NoSuchFieldException, IllegalAccessException {
        texSubImage2D(a, b, c, d, e, f, g, h, getBufferAddr(data), data.remaining() << 3);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL11C_glGetFloat_IF")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL11C_glGetFloat_IF'")
    public static native float glGetFloat_IF(int type);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL30C_glVertexAttribIPointer_IIIIJV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL30C_glVertexAttribIPointer_IIIIJV'")
    public static native void glVertexAttribIPointer_IIIIJV(int a, int b, int c, int d, long e);

    @NoThrow
    @JavaScript(code = "gl.drawArrays(arg0,arg1,arg2)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glDrawArrays_IIIV")
    public static native void glDrawArrays_IIIV(int mode, int first, int count);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDrawElements_IIIJV")
    @JavaScript(code = "gl.drawElements(arg0,arg1,arg2,Number(arg3))")
    public static native void glDrawElements_IIIJV(int mode, int count, int type, long nullPtr);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL40C_glDrawArraysIndirect_IJV")
    @JavaScript(code = "/* not supported */")
    public static native void glDrawArraysIndirect_IJV(int mode, long offset);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL40C_glDrawElementsIndirect_IIJV")
    @JavaScript(code = "/* not supported */")
    public static native void glDrawElementsIndirect_IIJV(int mode, int indexType, long offset);

    @NoThrow
    @JavaScript(code = "gl.drawArraysInstanced(arg0,arg1,arg2,arg3)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glDrawArraysInstanced_IIIIV")
    public static native void glDrawArraysInstanced_IIIIV(int mode, int first, int count, int primCount);

    @NoThrow
    @JavaScript(code = "gl.bufferData(arg0,new Uint8Array(memory.buffer,arg1,arg2),arg3)")
    private static native void bufferData8(int target, int addr, int length, int usage);

    @NoThrow
    @JavaScript(code = "gl.bufferData(arg0,new Uint16Array(memory.buffer,arg1,arg2),arg3)")
    private static native void bufferData16(int target, int addr, int length, int usage);

    @Alias(names = "org_lwjgl_opengl_GL15C_glBufferData_ILjava_nio_ByteBufferIV")
    public static void GL15C_glBufferData_ILjava_nio_ByteBufferIV(int target, ByteBuffer data, int usage)
            throws NoSuchFieldException, IllegalAccessException {
        bufferData8(target, getBufferAddr(data), data.remaining(), usage);
    }

    @Alias(names = "org_lwjgl_opengl_GL15C_glBufferData_ILjava_nio_ShortBufferIV")
    public static void GL15C_glBufferData_ILjava_nio_ShortBufferIV(int target, ShortBuffer data, int usage)
            throws NoSuchFieldException, IllegalAccessException {
        bufferData16(target, getBufferAddr(data), data.remaining(), usage);
    }

    @Alias(names = "org_lwjgl_opengl_GL15C_glBufferData_IAIIV")
    public static void org_lwjgl_opengl_GL15C_glBufferData_IAIIV(int target, int[] data, int usage) {
        bufferData8(target, getAddr(data) + arrayOverhead, data.length << 2, usage);
    }

    @NoThrow
    @JavaScript(code = "gl.bufferSubData(arg0,Number(arg1),new Uint8Array(memory.buffer,arg2,arg3))")
    private static native void bufferSubData8(int target, long offset, int addr, int length);

    @NoThrow
    @JavaScript(code = "gl.bufferSubData(arg0,Number(arg1),new Uint16Array(memory.buffer,arg2,arg3))")
    private static native void bufferSubData16(int target, long offset, int addr, int length);

    @Alias(names = "org_lwjgl_opengl_GL15C_glBufferSubData_IJLjava_nio_ByteBufferV")
    public static void GL15C_glBufferSubData_IJLjava_nio_ByteBufferV(int target, long offset, ByteBuffer data)
            throws NoSuchFieldException, IllegalAccessException {
        if (offset < 0 || offset > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Offset is out of bounds for WebGL!");
        bufferSubData8(target, offset, getBufferAddr(data), data.remaining());
    }

    @Alias(names = "org_lwjgl_opengl_GL15C_glBufferSubData_IJAIV")
    public static void GL15C_glBufferSubData_IJAIV(int target, long offset, int[] data) {
        if (offset < 0 || offset > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Offset is out of bounds for WebGL!");
        bufferSubData8(target, offset, getAddr(data) + arrayOverhead, data.length);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glBindVertexArray_IV")
    @JavaScript(code = "gl.bindVertexArray(unmap(arg0))")
    public static native void glBindVertexArray_IV(int buffer);

    @NoThrow
    @JavaScript(code = "gl.getAttribLocation(unmap(arg0),str(arg1))")
    public static native int glGetAttribLocation(int program, String name);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDeleteFramebuffers_IV")
    @JavaScript(code = "gl.deleteFramebuffer(unmap(arg0)); delete glMap[arg0];")
    public static native void glDeleteFramebuffers_IV(int id);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDeleteRenderbuffers_IV")
    @JavaScript(code = "gl.deleteRenderbuffer(unmap(arg0)); delete glMap[arg0];")
    public static native void glDeleteRenderbuffers_IV(int id);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDeleteRenderbuffers_AIV")
    @JavaScript(code = "gl.deleteRenderbuffer(arg0);")
    public static native void glDeleteRenderbuffers_AIV(int id);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDeleteTextures_IV")
    @JavaScript(code = "gl.deleteTexture(unmap(arg0)); delete glMap[arg0];")
    public static native void GLXXC_glDeleteTextures(int id);

    @Alias(names = "org_lwjgl_opengl_GL46C_glDeleteTextures_AIV")
    public static void org_lwjgl_opengl_GL11C_glDeleteTextures_AIV(int[] ids) {
        for (int id : ids) {
            GLXXC_glDeleteTextures(id);
        }
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glGetAttribLocation_ILjava_lang_CharSequenceI")
    public static int glGetAttribLocation_ILjava_lang_CharSequenceI(int program, CharSequence name) {
        return glGetAttribLocation(program, name.toString());
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glVertexAttribPointer_IIIZIJV")
    @JavaScript(code = "gl.vertexAttribPointer(arg0,arg1,arg2,!!arg3,arg4,Number(arg5))")
    public static native void glVertexAttribPointer_IIIZIJV(int index, int size, int type, boolean normalized, int stride, long ptr);

    @NoThrow
    @JavaScript(code = "gl.vertexAttrib1f(arg0,arg1)")
    @Alias(names = "org_lwjgl_opengl_GL46C_glVertexAttrib1f_IFV")
    public static native void glVertexAttrib1f_IFV(int index, float value);

    @NoThrow
    @JavaScript(code = "gl.vertexAttrib1f(arg0,arg1)") // int variant is not supported :/
    @Alias(names = "org_lwjgl_opengl_GL46C_glVertexAttrib1i_IIV")
    public static native void glVertexAttrib1i_IIV(int index, int value);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glVertexAttribDivisor_IIV")
    @JavaScript(code = "gl.vertexAttribDivisor(arg0,arg1)")
    public static native void glVertexAttribDivisor_IIV(int index, int divisor);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glEnableVertexAttribArray_IV")
    @JavaScript(code = "gl.enableVertexAttribArray(arg0)")
    public static native void glEnableVertexAttribArray_IV(int index);

    @NoThrow
    @JavaScript(code = "gl.bindAttribLocation(unmap(arg0),arg1,str(arg2))")
    public static native void glBindAttribLocation2(int program, int index, String name);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glBindAttribLocation_IILjava_lang_CharSequenceV")
    public static void glBindAttribLocation(int program, int index, CharSequence name) {
        glBindAttribLocation2(program, index, name.toString());
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDisableVertexAttribArray_IV")
    @JavaScript(code = "gl.disableVertexAttribArray(arg0)")
    public static native void glDisableVertexAttribArray_IV(int index);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glTexParameteri_IIIV")
    @JavaScript(code = "if(arg1!=33169) gl.texParameteri(arg0,arg1,arg2);") // GL_GENERATE_MIPMAP is not supported :/
    public static native void glTexParameteri_IIIV(int a, int b, int c);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glTexParameterf_IIFV")
    @JavaScript(code = "gl.texParameterf(arg0,arg1,arg2)")
    public static native void glTexParameterf_IIFV(int a, int b, float c);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL11C_glTexParameterfv_IIAFV")
    @JavaScript(code = "")
    public static native void glTexParameterfv_IIAFV(int a, int b, float[] c);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glFramebufferTexture2D_IIIIIV")
    @JavaScript(code = "/*console.log('glFramebufferTexture2D', arguments);*/gl.framebufferTexture2D(arg0,arg1,arg2,unmap(arg3),arg4)")
    public static native void glFramebufferTexture2D_IIIIIV(int a, int b, int c, int d, int e);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL30C_glFramebufferTextureLayer_IIIIIV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL30C_glFramebufferTextureLayer_IIIIIV'")
    public static native void glFramebufferTextureLayer_IIIIIV(int a, int b, int c, int d, int e);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glRenderbufferStorage_IIIIV")
    @JavaScript(code = "" +
            "if(arg1 == 33191 || arg1 == 6402) arg1 = 33190;\n" + // DEPTH_COMPONENT32 and DEPTH_COMPONENT isn't supported, but DEPTH_COMPONENT24 is
            "gl.renderbufferStorage(arg0,arg1,arg2,arg3)")
    public static native void glRenderbufferStorage(int target, int format, int width, int height);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL40C_glBlendEquationSeparatei_IIIV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL40C_glBlendEquationSeparatei_IIIV'")
    public static native void glBlendEquationSeparatei_IIIV(int a, int b, int c);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL30C_glRenderbufferStorageMultisample_IIIIIV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL30C_glRenderbufferStorageMultisample_IIIIIV'")
    public static native void glRenderbufferStorageMultisample_IIIIIV(int a, int b, int c, int d, int e);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL15C_glBeginQuery_IIV")
    @JavaScript(code = "if(arg0 != 35007) { gl.beginQuery(arg0,unmap(arg1)); } else { glTimeQueries[arg1] = 1; glTimeQuery = arg1; glTimer = performance.now(); }")
    // GL_TIME_ELAPSED isn't supported
    public static native void glBeginQuery_IIV(int a, int b);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL15C_glEndQuery_IV")
    @JavaScript(code = "if(arg0 != 35007) { gl.endQuery(arg0); } else { glTimeQueries[glTimeQuery] = (1e6 * (performance.now() - glTimer)) | 0; }")
    public static native void glEndQuery_IV(int a);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL15C_glGetQueryObjecti_III")
    @JavaScript(code = "return arg0 in glTimeQueries ? (arg1 == 0x8867 ? 1 : glTimeQueries[arg0]) : gl.getQueryParameter(unmap(arg0), arg1)")
    public static native int GL15C_glGetQueryObjecti_III(int a, int b);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL33C_glGetQueryObjecti64_IIJ")
    @JavaScript(code = "return arg0 in glTimeQueries ? (arg1 == 0x8867 ? 1 : glTimeQueries[arg0]) : gl.getQueryParameter(unmap(arg0), arg1)")
    public static native long GL33C_glGetQueryObjecti64_IIJ(int a, int b);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL42C_glMemoryBarrier_IV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL42C_glMemoryBarrier_IV'")
    public static native void glMemoryBarrier_IV(int a);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL32C_glTexImage2DMultisample_IIIIIZV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL32C_glTexImage2DMultisample_IIIIIZV'")
    public static native void glTexImage2DMultisample_IIIIIZV(int a, int b, int c, int d, int e, boolean f);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL42C_glBindImageTexture_IIIZIIIV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL42C_glBindImageTexture_IIIZIIIV'")
    public static native void glBindImageTexture_IIIZIIIV(int a, int b, int c, boolean d, int e, int f, int g);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL11C_glGetTexImage_IIIIAIV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL11C_glGetTexImage_IIIIAIV'")
    public static native void org_lwjgl_opengl_GL11C_glGetTexImage_IIIIAIV(int a, int b, int c, int d, int[] e);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIIADV")
    public static void org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIIADV(
            int a, int b, int c, int d, int e, int f, int g, int h, double[] data) {
        texImage2DAny(a, b, c, d, e, f, g, h, getAddr(data) + arrayOverhead, data.length << 3);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIIADV")
    public static void org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIIADV(
            int a, int b, int c, int d, int e, int f, int g, int h, double[] data) {
        texSubImage2D(a, b, c, d, e, f, g, h, getAddr(data) + arrayOverhead, data.length << 3);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIIAFV")
    public static void org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIIAFV(
            int a, int b, int c, int d, int e, int f, int g, int h, float[] data) {
        texSubImage2D(a, b, c, d, e, f, g, h, getAddr(data) + arrayOverhead, data.length << 2);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL12C_glTexImage3D_IIIIIIIIIAIV")
    public static void org_lwjgl_opengl_GL12C_glTexImage3D_IIIIIIIIIAIV(
            int a, int b, int c, int d, int e, int f, int g, int h, int i, int[] data) {
        texImage3DAny(a, b, c, d, e, f, g, h, i, getAddr(data) + arrayOverhead, data.length << 2);
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL30C_glVertexAttribI1i_IIV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL30C_glVertexAttribI1i_IIV'")
    public static native void glVertexAttribI1i_IIV(int a, int b);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL45C_glClipControl_IIV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL45C_glClipControl_IIV'")
    public static native void glClipControl_IIV(int a, int b);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL30C_glBindBufferBase_IIIV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL30C_glBindBufferBase_IIIV'")
    public static native void glBindBufferBase_IIIV(int a, int b, int c);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL43C_glDispatchCompute_IIIV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL43C_glDispatchCompute_IIIV'")
    public static native void org_lwjgl_opengl_GL43C_glDispatchCompute_IIIV(int a, int b, int c);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL40C_glBlendFuncSeparatei_IIIIIV")
    @JavaScript(code = "throw 'org_lwjgl_opengl_GL40C_glBlendFuncSeparatei_IIIIIV'")
    public static native void org_lwjgl_opengl_GL40C_glBlendFuncSeparatei_IIIIIV(int a, int b, int c, int d, int e);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glFramebufferRenderbuffer_IIIIV")
    @JavaScript(code = "/*console.log('glFramebufferRenderbuffer', arguments);*/gl.framebufferRenderbuffer(arg0,arg1,arg2,unmap(arg3))")
    public static native void glFramebufferRenderbuffer(int target, int attachment, int target1, int ptr);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDrawBuffer_IV")
    @JavaScript(code = "/*console.log('glDrawBuffer',arguments);*/gl.drawBuffers([arg0])")
    public static native void glDrawBuffer_IV(int mode);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL45C_glCreateVertexArrays_I")
    @JavaScript(code = "return map(gl.createVertexArray())")
    public static native int glCreateVertexArrays_I();

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDepthFunc_IV")
    @JavaScript(code = "gl.depthFunc(arg0)")
    public static native void glDepthFunc_IV(int func);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDepthRange_DDV")
    @JavaScript(code = "gl.depthRange(arg0,arg1)")
    public static native void glDepthRange_DDV(double a, double b);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDepthMask_ZV")
    @JavaScript(code = "gl.depthMask(!!arg0)")
    public static native void glDepthMask_ZV(boolean mask);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glCullFace_IV")
    @JavaScript(code = "gl.cullFace(arg0)")
    public static native void glCullFace_IV(int mode);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDrawElementsInstanced_IIIJIV")
    @JavaScript(code = "gl.drawElementsInstanced(arg0,arg1,arg2,0,arg4)")
    public static native void glDrawElementsInstanced_IIIJIV(int a, int b, int c, long ptr, int e);

    @NoThrow
    @JavaScript(code = "window.tmp=[]")
    private static native void drawBuffersCreate();

    @NoThrow
    @JavaScript(code = "window.tmp.push(arg0)")
    private static native void drawBuffersPush(int mode);

    @NoThrow
    @JavaScript(code = "/*console.log('glDrawBuffers', window.tmp);*/gl.drawBuffers(window.tmp);delete window.tmp")
    private static native void drawBuffersExec();

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glDrawBuffers_AIV")
    public static void GLXXC_glDrawBuffers_AIV(int[] modes) {
        drawBuffersCreate();
        for (int mode : modes) {
            drawBuffersPush(mode);
        }
        drawBuffersExec();
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glPixelStorei_IIV")
    public static void glPixelStorei_IIV(int a, int b) {
        // used for pixel alignment... used in WebGL?
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glCheckFramebufferStatus_II")
    @JavaScript(code = "/*console.log('glCheckFramebufferStatus');*/return gl.checkFramebufferStatus(arg0)")
    public static native int glCheckFramebufferStatus_II(int target);

    @NoThrow
    @JavaScript(code = "")
    public static native void objectLabel(int type, int ptr, String name);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glObjectLabel_IILjava_lang_CharSequenceV")
    public static void glObjectLabel_IILjava_lang_CharSequenceV(int type, int ptr, CharSequence name) {
        objectLabel(type, ptr, name.toString());
    }

    @NoThrow
    @Alias(names = "org_lwjgl_system_MemoryUtil_memAllocFloat_ILjava_nio_FloatBuffer")
    public static FloatBuffer MemoryUtil_memAllocFloat(int size) {
        return FloatBuffer.allocate(size);
    }

    @NoThrow
    @Alias(names = "static_org_lwjgl_opengl_GLDebugMessageCallbackI_V")
    public static void static_org_lwjgl_opengl_GLDebugMessageCallbackI_V() {
        // idc
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL15C_glGenQueries_AIV")
    private static void org_lwjgl_opengl_GL15C_glGenQueries_AIV(int[] ids) {
        for (int i = 0; i < ids.length; i++) {
            ids[i] = GL15C_glGenQueries_I();
        }
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL15C_glGenQueries_I")
    @JavaScript(code = "return map(gl.createQuery())")
    private static native int GL15C_glGenQueries_I();

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL15C_glDeleteQueries_IV")
    @JavaScript(code = "gl.deleteQuery(unmap(arg0))")
    private static native void org_lwjgl_opengl_GL15C_glDeleteQueries_IV(int id);

    @NoThrow
    @JavaScript(code = "")
    public static native void glPushDebugGroup(int target, int custom, String name);

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL43C_glPushDebugGroup_IILjava_lang_CharSequenceV")
    public static void glPushDebugGroup_IILjava_lang_CharSequenceV(int target, int custom, CharSequence name) {
        glPushDebugGroup(target, custom, name.toString());
    }

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL46C_glPopDebugGroup_V")
    @JavaScript(code = "")
    public static native void glPopDebugGroup_V();

    @NoThrow
    @Alias(names = "org_lwjgl_opengl_GL20C_glDrawBuffers_IV")
    @JavaScript(code = "gl.drawBuffers([arg0])")
    private static native void glDrawBuffers_IV(int i);

    @NoThrow
    @JavaScript(code = "") // not supported :(
    @Alias(names = "org_lwjgl_opengl_GL11C_glPolygonMode_IIV")
    public static native void glPolygonMode_IIV(int x, int y);

}
