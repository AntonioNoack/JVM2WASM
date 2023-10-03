package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import annotations.WASM;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.Callback;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.SharedLibrary;
import sun.misc.Unsafe;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import static jvm.JVM32.*;
import static jvm.JavaLang.getAddr;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_VERSION;
import static org.lwjgl.opengl.GL12C.GL_BGRA;
import static org.lwjgl.opengl.GL20C.GL_SHADING_LANGUAGE_VERSION;

public class LWJGLxOpenGL {

	@NoThrow
	@Alias(name = "org_lwjgl_system_MemoryAccessJNI_getPointerSize_I")
	@WASM(code = "i32.const 4")
	private static native int getPointerSize();

	@Alias(name = "org_lwjgl_system_MemoryUtil_memAlloc_ILjava_nio_ByteBuffer")
	public static ByteBuffer MemoryUtil_memAlloc(int size) {
		return ByteBuffer.allocate(size);
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL11C_glGetError_I|" +
			"org_lwjgl_opengl_GL20C_glGetError_I|" +
			"org_lwjgl_opengl_GL30C_glGetError_I")
	@JavaScript(code = "return gl.getError()")
	public static native int GL30C_glGetError_I();

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glCreateProgram_I")
	@JavaScript(code = "return map(gl.createProgram())")
	public static native int GL20C_glCreateProgram_I();

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glCreateShader_II")
	@JavaScript(code = "return map(gl.createShader(arg0))")
	public static native int GL20C_glCreateShader_II(int type);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glCompileShader_IV")
	@JavaScript(code = "gl.compileShader(unmap(arg0))")
	public static native void GL20C_glCompileShader_IV(int shader);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glLinkProgram_IV")
	@JavaScript(code = "gl.linkProgram(unmap(arg0))")
	public static native void GL20C_glLinkProgram_IV(int program);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glAttachShader_IIV")
	@JavaScript(code = "gl.attachShader(unmap(arg0), unmap(arg1))")
	public static native void GL20C_glAttachShader_IIV(int program, int shader);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glUseProgram_IV")
	@JavaScript(code = "gl.useProgram(unmap(arg0))")
	public static native void GL20C_glUseProgram_IV(int program);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glValidateProgram_IV")
	@JavaScript(code = "gl.validateProgram(unmap(arg0))")
	public static native void GL20C_glValidateProgram_IV(int program);

	@NoThrow
	@JavaScript(code = "return map(gl.getUniformLocation(unmap(arg0), str(arg1)))")
	public static native int GL20C_glGetUniformLocation2(int program, CharSequence name);

	@NoThrow
	@JavaScript(code = "gl.uniform1f(unmap(arg0),arg1)")
	@Alias(name = "org_lwjgl_opengl_GL20C_glUniform1f_IFV")
	public static native void org_lwjgl_opengl_GL20C_glUniform1f(int location, float x);

	@NoThrow
	@JavaScript(code = "gl.uniform1i(unmap(arg0),arg1)")
	@Alias(name = "org_lwjgl_opengl_GL20C_glUniform1i_IIV")
	public static native void org_lwjgl_opengl_GL20C_glUniform1i(int location, int x);

	@NoThrow
	@JavaScript(code = "gl.uniform2f(unmap(arg0),arg1,arg2)")
	@Alias(name = "org_lwjgl_opengl_GL20C_glUniform2f_IFFV")
	public static native void org_lwjgl_opengl_GL20C_glUniform2f(int location, float x, float y);

	@NoThrow
	@JavaScript(code = "gl.uniform2i(unmap(arg0),arg1,arg2)")
	@Alias(name = "org_lwjgl_opengl_GL20C_glUniform2i_IIIV")
	public static native void org_lwjgl_opengl_GL20C_glUniform2i(int location, int x, int y);

	@NoThrow
	@JavaScript(code = "gl.uniform3f(unmap(arg0),arg1,arg2,arg3)")
	@Alias(name = "org_lwjgl_opengl_GL20C_glUniform3f_IFFFV")
	public static native void org_lwjgl_opengl_GL20C_glUniform3f(int location, float x, float y, float z);

	@NoThrow
	@JavaScript(code = "gl.uniform3i(unmap(arg0),arg1,arg2,arg3)")
	@Alias(name = "org_lwjgl_opengl_GL20C_glUniform3i_IIIIV")
	public static native void org_lwjgl_opengl_GL20C_glUniform3i(int location, int x, int y, int z);

	@NoThrow
	@JavaScript(code = "gl.uniform4f(unmap(arg0),arg1,arg2,arg3,arg4)")
	@Alias(name = "org_lwjgl_opengl_GL20C_glUniform4f_IFFFFV")
	public static native void org_lwjgl_opengl_GL20C_glUniform4f(int location, float x, float y, float z, float w);

	@NoThrow
	@JavaScript(code = "gl.uniform4i(unmap(arg0),arg1,arg2,arg3,arg4)")
	@Alias(name = "org_lwjgl_opengl_GL20C_glUniform4i_IIIIIV")
	public static native void org_lwjgl_opengl_GL20C_glUniform4i(int location, int x, int y, int z, int w);

	@NoThrow
	@JavaScript(code = "gl.enable(arg0)")
	@Alias(name = "org_lwjgl_opengl_GL11C_glEnable_IV|" +
			"org_lwjgl_opengl_GL20C_glEnable_IV|" +
			"org_lwjgl_opengl_GL30C_glEnable_IV|" +
			"org_lwjgl_opengl_GL45C_glEnable_IV")
	public static native void GLXXC_glEnable(int mode);

	@NoThrow
	@JavaScript(code = "gl.disable(arg0)")
	@Alias(name = "org_lwjgl_opengl_GL11C_glDisable_IV|" +
			"org_lwjgl_opengl_GL20C_glDisable_IV|" +
			"org_lwjgl_opengl_GL30C_glDisable_IV|" +
			"org_lwjgl_opengl_GL45C_glDisable_IV")
	public static native void GLXXC_glDisable(int mode);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glGetUniformLocation_ILjava_lang_CharSequenceI")
	public static int GL20C_glGetUniformLocation(int program, CharSequence name) {
		return GL20C_glGetUniformLocation2(program, name.toString());
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glGenFramebuffers_I")
	@JavaScript(code = "return map(gl.createFramebuffer())")
	public static native int org_lwjgl_opengl_GL30C_glGenFramebuffers_I();

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glGenRenderbuffers_I")
	@JavaScript(code = "return map(gl.createRenderbuffer())")
	public static native int org_lwjgl_opengl_GL30C_glGenRenderbuffers_I();

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glBindFramebuffer_IIV")
	@JavaScript(code = "gl.bindFramebuffer(arg0,unmap(arg1))")
	public static native void org_lwjgl_opengl_GL30C_glBindFramebuffer_IIV(int type, int ptr);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glBindRenderbuffer_IIV")
	@JavaScript(code = "gl.bindRenderbuffer(arg0,unmap(arg1))")
	public static native void org_lwjgl_opengl_GL30C_glBindRenderbuffer_IIV(int type, int ptr);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL11C_glViewport_IIIIV")
	@JavaScript(code = "gl.viewport(arg0,arg1,arg2,arg3)")
	public static native void org_lwjgl_opengl_GL11C_glViewport_IIIIV(int x, int y, int w, int h);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glBlendEquationSeparate_IIV")
	@JavaScript(code = "gl.blendEquationSeparate(arg0,arg1)")
	public static native void org_lwjgl_opengl_GL30C_glBlendEquationSeparate_IIV(int a, int b);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glBlendFuncSeparate_IIIIV")
	@JavaScript(code = "gl.blendFuncSeparate(arg0,arg1,arg2,arg3)")
	public static native void org_lwjgl_opengl_GL30C_glBlendFuncSeparate_IIIIV(int a, int b, int c, int d);

	@NoThrow
	@JavaScript(code = "return map(gl.createTexture())")
	public static native int glGenTexture();

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL11C_glGenTextures_AIV")
	public static void org_lwjgl_opengl_GL11C_glGenTextures_AIV(int[] v) {
		for (int i = 0, l = v.length; i < l; i++) {
			v[i] = glGenTexture();
		}
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL11C_glClearColor_FFFFV")
	@JavaScript(code = "gl.clearColor(arg0,arg1,arg2,arg3)")
	public static native void org_lwjgl_opengl_GL11C_glClearColor_FFFFV(float r, float g, float b, float a);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL11C_glClearDepth_DV")
	@JavaScript(code = "gl.clearDepth(arg0)")
	public static native void org_lwjgl_opengl_GL11C_glClearDepth_DV(double depth);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL11C_glClear_IV")
	@JavaScript(code = "gl.clear(arg0)")
	public static native void org_lwjgl_opengl_GL11C_glClear_IV(int mask);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glDeleteProgram_IV")
	@JavaScript(code = "gl.deleteProgram(unmap(arg0))")
	public static native void org_lwjgl_opengl_GL20C_glDeleteProgram_IV(int program);

	// if statement for WebGL, because it doesn't have GL_TEXTURE_2D_MULTISAMPLE
	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL45C_glBindTexture_IIV")
	@JavaScript(code = "if(arg0 == 37120) arg0 = 3553; gl.bindTexture(arg0,unmap(arg1))")
	public static native void org_lwjgl_opengl_GL45C_glBindTexture_IIV(int mode, int tex);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL45C_glActiveTexture_IV")
	@JavaScript(code = "gl.activeTexture(arg0)")
	public static native void org_lwjgl_opengl_GL45C_glActiveTexture_IV(int id);

	// todo implement multisampling like https://stackoverflow.com/questions/47934444/webgl-framebuffer-multisampling
	// https://webglfundamentals.org/webgl/lessons/webgl-shaders-and-glsl.html
	// https://hacks.mozilla.org/2014/01/webgl-deferred-shading/
	// this doesn't use a texture, but a render buffer; as multi-sampled texture don't seem to exist in WebGL
    /*@NoThrow
    @Alias(name = "org_lwjgl_opengl_GL45C_glTexImage2DMultisample_IIIIIZV")
    @JavaScript(code = "gl.texImage2D()")
    public static native void org_lwjgl_opengl_GL45C_glTexImage2DMultisample_IIIIIZV(int target, int samples, int format, int w, int h, boolean fixedSampleLocations);
*/
	@Alias(name = "static_org_lwjgl_system_Struct_V")
	public static void Struct_clinit() {
		// crashes, because it doesn't find the correct field for reflections
		// however we don't need reflections in LWJGL, so I don't really care
	}

	@NoThrow
	@Alias(name = "org_lwjgl_system_MemoryUtil_memAddress_Ljava_nio_ByteBufferJ")
	public static long MemoryUtil_memAddress(ByteBuffer buffer) {
		return getAddr(buffer);
	}

	private static SharedLibrary lib;

	@NoThrow
	@Alias(name = "org_lwjgl_system_Library_loadNative_Ljava_lang_ClassLjava_lang_StringLjava_lang_StringZZLorg_lwjgl_system_SharedLibrary")
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

	@Alias(name = "org_lwjgl_system_FunctionProvider_getFunctionAddress_Ljava_lang_CharSequenceJ")
	public static long FunctionProvider_getFunctionAddress(FunctionProvider provider, CharSequence name) {
		return getAddr(name);
	}

	@Alias(name = "org_lwjgl_system_MemoryStack_mallocInt_ILjava_nio_IntBuffer")
	public static IntBuffer mallocInt(MemoryStack self, int size) {
		return IntBuffer.allocate(size);
	}

	@Alias(name = "org_lwjgl_system_MemoryUtil_getUnsafeInstance_Lsun_misc_Unsafe")
	public static Unsafe org_lwjgl_system_MemoryUtil_getUnsafeInstance_Lsun_misc_Unsafe() {
		return Unsafe.getUnsafe();
	}

	@Alias(name = "org_lwjgl_system_MemoryUtil_memAddress_Ljava_nio_IntBufferJ")
	public static long org_lwjgl_system_MemoryUtil_memAddress_Ljava_nio_IntBufferJ(IntBuffer buffer) {
		return getAddr(buffer);
	}

	@Alias(name = "org_lwjgl_system_Library_loadSystem_Ljava_util_function_ConsumerLjava_util_function_ConsumerLjava_lang_ClassLjava_lang_StringLjava_lang_StringV")
	public static <V> void org_lwjgl_system_Library_loadSystem(Consumer<V> x, Consumer<V> y, Class<V> ctx, String module, String name) {
	}

	@Alias(name = "org_lwjgl_opengl_GL_create_V")
	public static void org_lwjgl_opengl_GL_create_V() {
	}

	@Alias(name = "org_lwjgl_opengl_GL20C_glShaderSource_ILjava_lang_CharSequenceV")
	@JavaScript(code = "gl.shaderSource(unmap(arg0),str(arg1).split('#extension').join('// #ext'))")
	public static native void org_lwjgl_opengl_GL20C_glShaderSource_ILjava_lang_CharSequenceV(int shader, CharSequence source);

	@NoThrow
	@JavaScript(code = "return fill(arg0, gl.getShaderInfoLog(unmap(arg1)))")
	public static native int fillShaderInfoLog(char[] data, int shader);

	@NoThrow
	@JavaScript(code = "return fill(arg0, gl.getProgramInfoLog(unmap(arg1)))")
	public static native int fillProgramInfoLog(char[] data, int program);


	@Alias(name = "org_lwjgl_opengl_GL20C_glGetShaderInfoLog_ILjava_lang_String")
	public static String GL20_glGetShaderInfoLog(int shader) {
		char[] buffer = FillBuffer.getBuffer();
		int length = fillShaderInfoLog(buffer, shader);
		if (length == 0) return "";
		return new String(buffer, 0, length);
	}

	@Alias(name = "org_lwjgl_opengl_GL20C_glGetProgramInfoLog_ILjava_lang_String")
	public static String GL20_glGetProgramInfoLog(int program) {
		char[] buffer = FillBuffer.getBuffer();
		int length = fillProgramInfoLog(buffer, program);
		if (length == 0) return "";
		return new String(buffer, 0, length);
	}

	@NoThrow
	@JavaScript(code = "return gl.getProgramParameter(unmap(arg0),arg1)")
	@Alias(name = "org_lwjgl_opengl_GL20C_glGetProgrami_III")
	public static native int org_lwjgl_opengl_GL20C_glGetProgrami_III(int program, int type);

	@Alias(name = "org_lwjgl_opengl_GL_createCapabilities_Lorg_lwjgl_opengl_GLCapabilities")
	public static GLCapabilities org_lwjgl_opengl_GL_createCapabilities() {
		return null;
	}

	@Alias(name = "org_lwjgl_opengl_GLUtil_setupDebugMessageCallback_Ljava_io_PrintStreamLorg_lwjgl_system_Callback")
	public static Callback setupDebugMessageCallback(PrintStream stream) {
		return null;
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL45C_glGenerateMipmap_IV")
	@JavaScript(code = "gl.generateMipmap(arg0)")
	public static native void org_lwjgl_opengl_GL45C_glGenerateMipmap_IV(int target);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL11C_glGetInteger_II")
	// 0x826e = max uniform locations; not defined in WebGL
	// 0x8D57 = max samples;
	// 0x821B, 0x821C = major, minor version
	// @JavaScript(code = "if(arg0 == 0x826E) return 1024; if(arg0 == 0x8D57) return 1; return gl.getParameter(arg0)")
	@JavaScript(code = "if(arg0 == 0x826E) return 1024; if(arg0 == 0x821B || arg0 == 0x821C) return 0; return gl.getParameter(arg0)")
	public static native int GL11C_glGetInteger(int i);

	@Alias(name = "org_lwjgl_opengl_GL11C_glGetIntegerv_IAIV")
	public static void org_lwjgl_opengl_GL11C_glGetIntegerv_IAIV(int id, int[] dst) {
		throw new IllegalStateException("Operation not (yet?) supported, use glGetInteger or ask me");
	}

	@Alias(name = "org_lwjgl_opengl_GL11C_glGetString_ILjava_lang_String")
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
	@JavaScript(code = "gl.texImage2D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,null)") // null is needed why-ever...
	private static native void GL11C_glTexImage2D(int target, int level, int format, int w, int h, int border, int dataFormat, int dataType);

	@NoThrow
	@JavaScript(code = "gl.texImage3D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8,null)") // null is needed why-ever...
	private static native void GL11C_glTexImage3D(int target, int level, int format, int w, int h, int d, int border, int dataFormat, int dataType);

	@NoThrow
	@JavaScript(code = "" +
			"gl.texImage2D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7," +
			"   arg7 == gl.UNSIGNED_INT ?" +
			"   new Uint32Array(memory.buffer, arg8, arg9>>2):" +
			"   arg7 == gl.FLOAT ?" +
			"   new Float32Array(memory.buffer, arg8, arg9>>2):" +
			"   arg7 == 0x140B ?" + // half float -> short
			"   new Uint16Array(memory.buffer, arg8, arg9>>1):" +
			"   new Uint8Array(memory.buffer, arg8, arg9))")
	private static native void GL11C_glTexImage2D(int target, int level, int format, int w, int h, int border, int dataFormat, int dataType, int ptr, int length);

	@Alias(name = "org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIIAFV")
	private static void org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIIAFV(
			int target, int level, int format, int w,
			int h, int border, int dataFormat, int dataType, float[] data) {
		GL11C_glTexImage2D(target, level, format, w, h, border, dataFormat, dataType, getAddr(data) + arrayOverhead, data.length << 2);
	}

	@NoThrow
	@JavaScript(code = "" +
			"gl.texImage3D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8," +
			"   arg8 == gl.UNSIGNED_INT ?" +
			"   new Uint32Array(memory.buffer, arg9, arg10>>2):" +
			"   arg8 == gl.FLOAT ?" +
			"   new Float32Array(memory.buffer, arg9, arg10>>2):" +
			"   new Uint8Array(memory.buffer, arg9, arg10))")
	private static native void GL11C_glTexImage3D(int target, int level, int format, int w, int h, int d, int border, int dataFormat, int dataType, int ptr, int length);

	@NoThrow
	@JavaScript(code = "" +
			"gl.texSubImage2D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7," +
			"   arg7 == gl.UNSIGNED_INT ?" +
			"   new Uint32Array(memory.buffer, arg8, arg9>>2):" +
			"   arg7 == gl.FLOAT ?" +
			"   new Float32Array(memory.buffer, arg8, arg9>>2):" +
			"   arg7 == 0x140B ?" + // half float -> short
			"   new Uint16Array(memory.buffer, arg8, arg9>>1):" +
			"   new Uint8Array(memory.buffer, arg8, arg9))")
	private static native void GL11C_glTexSubImage2D(int target, int level, int x, int y, int w, int h, int dataFormat, int dataType, int ptr, int length);

	@Alias(name = "org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIIAIV")
	public static void org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIIAIV(
			int target, int level, int x, int y,
			int w, int h, int dataFormat, int dataType, int[] data) {
		boolean swizzle = dataFormat == GL_BGRA;
		if (swizzle) dataFormat = GL_RGBA;
		if (swizzle) rgba2argb(data);
		GL11C_glTexSubImage2D(target, level, x, y, w, h, dataFormat, dataType, getAddr(data) + arrayOverhead, data.length << 2);
		if (swizzle) argb2rgba(data);
	}

	@NoThrow
	@JavaScript(code = "gl.scissor(arg0,arg1,arg2,arg3)")
	@Alias(name = "org_lwjgl_opengl_GL11C_glScissor_IIIIV")
	private static native void org_lwjgl_opengl_GL11C_glScissor_IIIIV(int x, int y, int w, int h);

	@NoThrow
	@JavaScript(code = "gl.flush()")
	@Alias(name = "org_lwjgl_opengl_GL11C_glFlush_V")
	private static native void org_lwjgl_opengl_GL11C_glFlush_V();

	@NoThrow
	@JavaScript(code = "gl.finish()")
	@Alias(name = "org_lwjgl_opengl_GL11C_glFinish_V")
	private static native void org_lwjgl_opengl_GL11C_glFinish_V();

	@NoThrow
	@JavaScript(code = "gl.readPixels(arg0,arg1,arg2,arg3,arg4,arg5,new Uint8Array(memory.buffer,arg6,arg7))")
	public static native void org_lwjgl_opengl_GL11C_glReadPixels_IIIIIIAIV(int x, int y, int w, int h, int format, int type, int data, int length);

	@Alias(name = "org_lwjgl_opengl_GL11C_glReadPixels_IIIIIIAIV")
	public static void org_lwjgl_opengl_GL11C_glReadPixels_IIIIIIAIV(int x, int y, int w, int h, int format, int type, int[] data) {
		org_lwjgl_opengl_GL11C_glReadPixels_IIIIIIAIV(x, y, w, h, format, type, getAddr(data) + arrayOverhead, data.length << 2);
	}

	@NoThrow
	@JavaScript(code = "gl.readPixels(arg0,arg1,arg2,arg3,arg4,arg5,new Float32Array(memory.buffer,arg6,arg7))")
	public static native void glReadPixels_IIIIIIAFV(int x, int y, int w, int h, int format, int type, int dataPtr, int length);

	@Alias(name = "org_lwjgl_opengl_GL11C_glReadPixels_IIIIIIAFV")
	public static void GL11C_glReadPixels_IIIIIIAFV(int x, int y, int w, int h, int format, int type, float[] data) {
		glReadPixels_IIIIIIAFV(x, y, w, h, format, type, getAddr(data) + arrayOverhead, data.length);
	}

	@Alias(name = "org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIILjava_nio_ByteBufferV")
	public static void org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIILjava_nio_ByteBufferV(
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
			GL11C_glTexImage2D(target, level, format, w, h, border, dataFormat, dataType);
		} else {
			GL11C_glTexImage2D(target, level, format, w, h, border, dataFormat, dataType,
					getBufferAddr(data), data.remaining());
		}
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIIAIV")
	public static void org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIIAIV(
			int target, int level, int format, int w, int h, int border,
			int dataFormat, int dataType, int[] data) {
		boolean swizzle = dataFormat == GL_BGRA;
		if (swizzle) dataFormat = GL_RGBA;
		if (data == null) {
			GL11C_glTexImage2D(target, level, format, w, h, border, dataFormat, dataType);
		} else {
			if (swizzle) rgba2argb(data);
			GL11C_glTexImage2D(target, level, format, w, h, border, dataFormat, dataType,
					getAddr(data) + arrayOverhead, data.length << 2);
			if (swizzle) argb2rgba(data);
		}
	}

	@Alias(name = "org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIILjava_nio_FloatBufferV")
	public static void org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIILjava_nio_FloatBufferV(
			int target, int level, int format, int w, int h, int border,
			int dataFormat, int dataType, FloatBuffer data
	) throws NoSuchFieldException, IllegalAccessException {
		if (data == null) {
			GL11C_glTexImage2D(target, level, format, w, h, border, dataFormat, dataType);
		} else {
			GL11C_glTexImage2D(target, level, format, w, h, border, dataFormat, dataType,
					getBufferAddr(data), data.remaining() << 2);
		}
	}

	@Alias(name = "org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIILjava_nio_ShortBufferV")
	public static void org_lwjgl_opengl_GL11C_glTexImage2D_IIIIIIIILjava_nio_ShortBufferV(
			int target, int level, int format, int w, int h, int border,
			int dataFormat, int dataType, ShortBuffer data
	) throws NoSuchFieldException, IllegalAccessException {
		if (data == null) {
			GL11C_glTexImage2D(target, level, format, w, h, border, dataFormat, dataType);
		} else {
			GL11C_glTexImage2D(target, level, format, w, h, border, dataFormat, dataType,
					getBufferAddr(data), data.remaining() << 1);
		}
	}

	@Alias(name = "org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIILjava_nio_FloatBufferV")
	public static void org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIILjava_nio_FloatBufferV(
			int target, int level, int format, int w, int h, int border,
			int dataFormat, int dataType, FloatBuffer data
	) throws NoSuchFieldException, IllegalAccessException {
		GL11C_glTexSubImage2D(target, level, format, w, h, border, dataFormat, dataType,
				getBufferAddr(data), data.remaining() << 2);
	}

	@Alias(name = "org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIILjava_nio_ShortBufferV")
	public static void org_lwjgl_opengl_GL11C_glTexSubImage2D_IIIIIIIILjava_nio_ShortBufferV(
			int target, int level, int format, int w, int h, int border,
			int dataFormat, int dataType, ShortBuffer data
	) throws NoSuchFieldException, IllegalAccessException {
		GL11C_glTexSubImage2D(target, level, format, w, h, border, dataFormat, dataType,
				getBufferAddr(data), data.remaining() << 1);
	}

	@Alias(name = "org_lwjgl_opengl_GL12C_glTexImage3D_IIIIIIIIILjava_nio_ByteBufferV")
	public static void org_lwjgl_opengl_GL12C_glTexImage3D_IIIIIIIIILjava_nio_ByteBufferV(
			int target, int level, int format, int w, int h, int d, int border,
			int dataFormat, int dataType, ByteBuffer data) throws NoSuchFieldException, IllegalAccessException {
		if (data == null) {
			GL11C_glTexImage3D(target, level, format, w, h, d, border, dataFormat, dataType);
		} else {
			GL11C_glTexImage3D(target, level, format, w, h, d, border, dataFormat, dataType,
					getBufferAddr(data), data.remaining());
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
	@Alias(name = "org_lwjgl_opengl_GL11C_glTexParameteriv_IIAIV")
	// @JavaScript(code = "gl.texParameteriv(arg0,arg1,new Uint32Array(memory.buffer,arg2+arrayOverhead,lib.r32(arg2+objectOverhead)))")
	public static void org_lwjgl_opengl_GL11C_glTexParameteriv_IIAIV(int target, int mode, int[] value) {
		if (mode == 0x8E46) log("Warning(glTexParameteriv): texture swizzling is not supported in WebGL");
		else log("Warning(glTexParameteriv)! Unknown mode", mode);
	}

	private static byte[] getBuffer(ByteBuffer data) throws NoSuchFieldException, IllegalAccessException {
		return (byte[]) data.getClass().getField("hb").get(data);
	}

	private static int getBufferAddr(FloatBuffer data) throws NoSuchFieldException, IllegalAccessException {
		Class<?> clazz = data.getClass();
		// ByteBuffer.allocate(10).asFloatBuffer();
		float[] floats = (float[]) clazz.getField("hb").get(data);
		int offset0 = arrayOverhead + (data.position() << 2);
		// java.nio.HeapFloatBuffer, is created from FloatBuffer.allocate()
		if (floats != null) return getAddr(floats) + offset0;
		// java.nio.ByteBufferAsFloatBufferL, is created from asFloatBuffer()
		ByteBuffer data2 = (ByteBuffer) clazz.getField("bb").get(data);
		byte[] bytes = data2.array();
		int offset = clazz.getField("offset").getInt(data);
		return getAddr(bytes) + offset + offset0;
	}

	private static int getBufferAddr(ShortBuffer data) throws NoSuchFieldException, IllegalAccessException {
		Class<?> clazz = data.getClass();
		// ByteBuffer.allocate(10).asShortBuffer();
		short[] floats = (short[]) clazz.getField("hb").get(data);
		int offset0 = arrayOverhead + (data.position() << 1);
		// java.nio.HeapFloatBuffer, is created from FloatBuffer.allocate()
		if (floats != null) return getAddr(floats) + offset0;
		// java.nio.ByteBufferAsFloatBufferL, is created from asFloatBuffer()
		ByteBuffer data2 = (ByteBuffer) clazz.getField("bb").get(data);
		byte[] bytes = data2.array();
		int offset = clazz.getField("offset").getInt(data);
		return getAddr(bytes) + offset + offset0;
	}

	@NoThrow
	@JavaScript(code = "gl.uniform1fv(unmap(arg0), new Float32Array(memory.buffer, arg1, arg2))")
	public static native void glUniform1fv(int location, int addr, int length);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glUniform1fv_ILjava_nio_FloatBufferV")
	public static void org_lwjgl_opengl_GL20C_glUniform1fv_ILjava_nio_FloatBufferV(int location, FloatBuffer data) throws NoSuchFieldException, IllegalAccessException {
		glUniform1fv(location, getBufferAddr(data), data.remaining());
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glUniform1fv_IAFV")
	public static void org_lwjgl_opengl_GL20C_glUniform1fv_IAFV(int location, float[] data) {
		glUniform1fv(location, getAddr(data) + arrayOverhead, data.length);
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glGetIntegeri_v_IIAIV")
	public static void org_lwjgl_opengl_GL30C_glGetIntegeri_v_IIAIV(int query, int index, int[] dst) {
		throwJs("Not supported/implemented");
	}

	@NoThrow
	@JavaScript(code = "gl.uniform4fv(unmap(arg0), new Float32Array(memory.buffer, arg1, arg2))")
	public static native void glUniform4fv(int location, int addr, int length);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glUniform4fv_ILjava_nio_FloatBufferV")
	public static void org_lwjgl_opengl_GL20C_glUniform4fv_ILjava_nio_FloatBufferV(int location, FloatBuffer data) throws NoSuchFieldException, IllegalAccessException {
		glUniform4fv(location, getBufferAddr(data), data.remaining());
	}

	@NoThrow
	@JavaScript(code = "gl.uniformMatrix4x3fv(unmap(arg0), arg1, new Float32Array(memory.buffer, arg2, arg3))")
	public static native void glUniformMatrix4x3fv(int location, boolean transpose, int addr, int length);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL21C_glUniformMatrix4x3fv_IZLjava_nio_FloatBufferV")
	public static void GL21C_glUniformMatrix4x3fv(int location, boolean transpose, FloatBuffer data) throws NoSuchFieldException, IllegalAccessException {
		int addr = getBufferAddr(data);
		glUniformMatrix4x3fv(location, transpose, addr, data.remaining());
	}

	@NoThrow
	@JavaScript(code = "gl.uniformMatrix4fv(unmap(arg0), arg1, new Float32Array(memory.buffer, arg2, arg3))")
	public static native void glUniformMatrix4fv(int location, boolean transpose, int addr, int length);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glUniformMatrix4fv_IZLjava_nio_FloatBufferV")
	public static void GL20C_glUniformMatrix4fv(int location, boolean transpose, FloatBuffer data) throws NoSuchFieldException, IllegalAccessException {
		int addr = getBufferAddr(data);
		glUniformMatrix4fv(location, transpose, addr, data.remaining());
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL15C_glGenBuffers_I")
	@JavaScript(code = "return map(gl.createBuffer())")
	public static native int org_lwjgl_opengl_GL15C_glGenBuffers_I();

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL15C_glBindBuffer_IIV")
	@JavaScript(code = "gl.bindBuffer(arg0,unmap(arg1))")
	public static native int org_lwjgl_opengl_GL15C_glBindBuffer_IIV(int target, int buffer);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL11C_glDrawElements_IIIJV")
	@JavaScript(code = "gl.drawElements(arg0,arg1,arg2,Number(arg3))")
	public static native void org_lwjgl_opengl_GL11C_glDrawElements_IIIJV(int mode, int count, int type, long nullPtr);

	@NoThrow
	@JavaScript(code = "gl.drawArrays(arg0,arg1,arg2)")
	@Alias(name = "org_lwjgl_opengl_GL33C_glDrawArrays_IIIV")
	public static native void org_lwjgl_opengl_GL33C_glDrawArrays_IIIV(int mode, int first, int count);

	@NoThrow
	@JavaScript(code = "gl.drawArraysInstanced(arg0,arg1,arg2,arg3)")
	@Alias(name = "org_lwjgl_opengl_GL31C_glDrawArraysInstanced_IIIIV|org_lwjgl_opengl_GL33C_glDrawArraysInstanced_IIIIV")
	public static native void org_lwjgl_opengl_GL33C_glDrawArraysInstanced_IIIIV(int mode, int first, int count, int primCount);

	@NoThrow
	@JavaScript(code = "gl.bufferData(arg0,new Uint8Array(memory.buffer,arg1,arg2),arg3)")
	private static native void glBufferData8(int target, int addr, int length, int usage);

	@NoThrow
	@JavaScript(code = "gl.bufferData(arg0,new Uint16Array(memory.buffer,arg1,arg2),arg3)")
	private static native void glBufferData16(int target, int addr, int length, int usage);

	private static int getBufferAddr(ByteBuffer data) throws NoSuchFieldException, IllegalAccessException {
		byte[] bytes = getBuffer(data);
		return getAddr(bytes) + data.position() + arrayOverhead;
	}

	@Alias(name = "org_lwjgl_opengl_GL15C_glBufferData_ILjava_nio_ByteBufferIV")
	public static void org_lwjgl_opengl_GL15C_glBufferData_ILjava_nio_ByteBufferIV(int target, ByteBuffer data, int usage)
			throws NoSuchFieldException, IllegalAccessException {
		glBufferData8(target, getBufferAddr(data), data.remaining(), usage);
	}

	@Alias(name = "org_lwjgl_opengl_GL15C_glBufferData_ILjava_nio_ShortBufferIV")
	public static void org_lwjgl_opengl_GL15C_glBufferData_ILjava_nio_ShortBufferIV(int target, ShortBuffer data, int usage)
			throws NoSuchFieldException, IllegalAccessException {
		glBufferData16(target, getBufferAddr(data), data.remaining(), usage);
	}

	@Alias(name = "org_lwjgl_opengl_GL15C_glBufferData_IAIIV")
	public static void org_lwjgl_opengl_GL15C_glBufferData_IAIIV(int target, int[] data, int usage) {
		glBufferData8(target, getAddr(data) + arrayOverhead, data.length << 2, usage);
	}

	@NoThrow
	@JavaScript(code = "gl.bufferSubData(arg0,arg1,new Uint8Array(memory.buffer,arg2,arg3))")
	private static native void glBufferSubData8(int target, int offset, int addr, int length);

	@Alias(name = "org_lwjgl_opengl_GL15C_glBufferSubData_IJLjava_nio_ByteBufferV")
	public static void org_lwjgl_opengl_GL15C_glBufferSubData_IJLjava_nio_ByteBufferV(int target, long offset, ByteBuffer data)
			throws NoSuchFieldException, IllegalAccessException {
		if (offset < 0 || offset > Integer.MAX_VALUE)
			throw new IllegalArgumentException("Offset is out of bounds for WebGL!");
		glBufferSubData8(target, (int) offset, getBufferAddr(data), data.remaining());
	}

	@Alias(name = "org_lwjgl_opengl_GL15C_glBufferSubData_IJAIV")
	public static void org_lwjgl_opengl_GL15C_glBufferSubData_IJAIV(int target, long offset, int[] data) {
		if (offset < 0 || offset > Integer.MAX_VALUE)
			throw new IllegalArgumentException("Offset is out of bounds for WebGL!");
		glBufferSubData8(target, (int) offset, getAddr(data) + arrayOverhead, data.length);
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glBindVertexArray_IV")
	@JavaScript(code = "gl.bindVertexArray(unmap(arg0))")
	public static native void org_lwjgl_opengl_GL30C_glBindVertexArray_IV(int buffer);

	@NoThrow
	@JavaScript(code = "gl.getAttribLocation(unmap(arg0),str(arg1))")
	public static native int glGetAttribLocation(int program, String name);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glDeleteFramebuffers_IV")
	@JavaScript(code = "gl.deleteFramebuffer(unmap(arg0)); delete glMap[arg0];")
	public static native void org_lwjgl_opengl_GL30C_glDeleteFramebuffers_IV(int id);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glDeleteRenderbuffers_IV")
	@JavaScript(code = "gl.deleteRenderbuffer(unmap(arg0)); delete glMap[arg0];")
	public static native void org_lwjgl_opengl_GL30C_glDeleteRenderbuffers_IV(int id);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glDeleteRenderbuffers_AIV")
	@JavaScript(code = "gl.deleteRenderbuffer(arg0);")
	public static native void org_lwjgl_opengl_GL30C_glDeleteRenderbuffers_AIV(int id);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL11C_glDeleteTextures_IV")
	@JavaScript(code = "gl.deleteTexture(unmap(arg0)); delete glMap[arg0];")
	public static native void GLXXC_glDeleteTextures(int id);

	@Alias(name = "org_lwjgl_opengl_GL11C_glDeleteTextures_AIV")
	public static void org_lwjgl_opengl_GL11C_glDeleteTextures_AIV(int[] ids) {
		for (int id : ids) {
			GLXXC_glDeleteTextures(id);
		}
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glGetAttribLocation_ILjava_lang_CharSequenceI")
	public static int org_lwjgl_opengl_GL20C_glGetAttribLocation_ILjava_lang_CharSequenceI(int program, CharSequence name) {
		return glGetAttribLocation(program, name.toString());
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glVertexAttribPointer_IIIZIJV")
	@JavaScript(code = "gl.vertexAttribPointer(arg0,arg1,arg2,!!arg3,arg4,Number(arg5))")
	public static native void org_lwjgl_opengl_GL20C_glVertexAttribPointer_IIIZIJV(int index, int size, int type, boolean normalized, int stride, long ptr);

	@NoThrow
	@JavaScript(code = "gl.vertexAttrib1f(arg0,arg1)")
	@Alias(name = "org_lwjgl_opengl_GL33C_glVertexAttrib1f_IFV")
	public static native void org_lwjgl_opengl_GL33C_glVertexAttrib1f_IFV(int index, float value);

	@NoThrow
	@JavaScript(code = "gl.vertexAttrib1f(arg0,arg1)") // int variant is not supported :/
	@Alias(name = "org_lwjgl_opengl_GL33C_glVertexAttrib1i_IIV")
	public static native void org_lwjgl_opengl_GL33C_glVertexAttrib1i_IIV(int index, int value);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL33C_glVertexAttribDivisor_IIV")
	@JavaScript(code = "gl.vertexAttribDivisor(arg0,arg1)")
	public static native void org_lwjgl_opengl_GL33C_glVertexAttribDivisor_IIV(int index, int divisor);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL33C_glEnableVertexAttribArray_IV")
	@JavaScript(code = "gl.enableVertexAttribArray(arg0)")
	public static native void org_lwjgl_opengl_GL33C_glEnableVertexAttribArray_IV(int index);

	@NoThrow
	@JavaScript(code = "gl.bindAttribLocation(unmap(arg0),arg1,str(arg2))")
	public static native void glBindAttribLocation2(int program, int index, String name);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glBindAttribLocation_IILjava_lang_CharSequenceV")
	public static void glBindAttribLocation(int program, int index, CharSequence name) {
		glBindAttribLocation2(program, index, name.toString());
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL33C_glDisableVertexAttribArray_IV")
	@JavaScript(code = "gl.disableVertexAttribArray(arg0)")
	public static native void org_lwjgl_opengl_GL33C_glDisableVertexAttribArray_IV(int index);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glTexParameteri_IIIV|org_lwjgl_opengl_GL45C_glTexParameteri_IIIV")
	@JavaScript(code = "if(arg1!=33169) gl.texParameteri(arg0,arg1,arg2);") // GL_GENERATE_MIPMAP is not supported :/
	public static native void org_lwjgl_opengl_GL45C_glTexParameteri_IIIV(int a, int b, int c);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL45C_glTexParameterf_IIFV")
	@JavaScript(code = "gl.texParameterf(arg0,arg1,arg2)")
	public static native void org_lwjgl_opengl_GL45C_glTexParameterf_IIFV(int a, int b, float c);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glFramebufferTexture2D_IIIIIV")
	@JavaScript(code = "gl.framebufferTexture2D(arg0,arg1,arg2,unmap(arg3),arg4)")
	public static native void org_lwjgl_opengl_GL30C_glFramebufferTexture2D_IIIIIV(int a, int b, int c, int d, int e);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glRenderbufferStorage_IIIIV")
	@JavaScript(code = "gl.renderbufferStorage(arg0,arg1,arg2,arg3)")
	public static native void org_lwjgl_opengl_GL30C_glRenderbufferStorage(int target, int format, int width, int height);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glFramebufferRenderbuffer_IIIIV")
	@JavaScript(code = "gl.framebufferRenderbuffer(arg0,arg1,arg2,unmap(arg3))")
	public static native void org_lwjgl_opengl_GL30C_glFramebufferRenderbuffer(int target, int attachment, int target1, int ptr);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL11C_glDrawBuffer_IV|org_lwjgl_opengl_GL30C_glDrawBuffer_IV")
	@JavaScript(code = "gl.drawBuffers([arg0])")
	public static native void org_lwjgl_opengl_GL30C_glDrawBuffer_IV(int mode);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL45C_glDepthFunc_IV")
	@JavaScript(code = "gl.depthFunc(arg0)")
	public static native void org_lwjgl_opengl_GL45C_glDepthFunc_IV(int func);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL45C_glDepthRange_DDV")
	@JavaScript(code = "gl.depthRange(arg0,arg1)")
	public static native void org_lwjgl_opengl_GL45C_glDepthRange_DDV(double a, double b);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL45C_glDepthMask_ZV")
	@JavaScript(code = "gl.depthMask(!!arg0)")
	public static native void org_lwjgl_opengl_GL45C_glDepthMask_ZV(boolean mask);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL45C_glCullFace_IV")
	@JavaScript(code = "gl.cullFace(arg0)")
	public static native void org_lwjgl_opengl_GL45C_glCullFace_IV(int mode);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL31C_glDrawElementsInstanced_IIIJIV")
	@JavaScript(code = "gl.drawElementsInstanced(arg0,arg1,arg2,0,arg4)")
	public static native void org_lwjgl_opengl_GL31C_glDrawElementsInstanced_IIIJIV(int a, int b, int c, long ptr, int e);

	@NoThrow
	@JavaScript(code = "window.tmp=[]")
	private static native void drawBuffersCreate();

	@NoThrow
	@JavaScript(code = "window.tmp.push(arg0)")
	private static native void drawBuffersPush(int mode);

	@NoThrow
	@JavaScript(code = "gl.drawBuffers(window.tmp);delete window.tmp")
	private static native void drawBuffersExec();

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL20C_glDrawBuffers_AIV|org_lwjgl_opengl_GL30C_glDrawBuffers_AIV")
	public static void GLXXC_glDrawBuffers_AIV(int[] modes) {
		drawBuffersCreate();
		for (int mode : modes) {
			drawBuffersPush(mode);
		}
		drawBuffersExec();
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glPixelStorei_IIV")
	public static void org_lwjgl_opengl_GL30C_glPixelStorei_IIV(int a, int b) {
		// used for pixel alignment... used in WebGL?
	}

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL30C_glCheckFramebufferStatus_II")
	@JavaScript(code = "return gl.checkFramebufferStatus(arg0)")
	public static native int org_lwjgl_opengl_GL30C_glCheckFramebufferStatus_II(int target);

	@NoThrow
	@Alias(name = "org_lwjgl_opengl_GL43C_glObjectLabel_IILjava_lang_CharSequenceV")
	public static void org_lwjgl_opengl_GL43C_glObjectLabel_IILjava_lang_CharSequenceV(int type, int ptr, CharSequence name) {
		// could be helpful in the future
	}

	@NoThrow
	@Alias(name = "org_lwjgl_system_MemoryUtil_memAllocFloat_ILjava_nio_FloatBuffer")
	public static FloatBuffer MemoryUtil_memAllocFloat(int size) {
		return FloatBuffer.allocate(size);
	}

	@NoThrow
	@Alias(name = "static_org_lwjgl_opengl_GLDebugMessageCallbackI_V")
	public static void static_org_lwjgl_opengl_GLDebugMessageCallbackI_V() {
		// idc
	}

}
