
#include <chrono>
#include <cmath>
#include <iostream>
#include <iomanip>
#include <string>
#include <filesystem>
#include <fstream>

#include <glad/gl.h>
#include <GLFW/glfw3.h>

// #define STANDALONE
#ifdef STANDALONE

#include "jvm2wasm-types.h"

void* memory = nullptr;

void initFunctionTable() {}
i32 global_G0 = 2798416;

i32i32 java_lang_String_length_I(i32 p0) {
    return { 0, 0 };
}

i32 engine_Engine_main_Ljava_lang_StringZZV(i32, i32, i32) {
    return 0;
}

i32 engine_Engine_update_IIFV(i32, i32, f32) {
    return 0;
}

void gc() {

}

#else
#include "jvm2wasm.cpp"
#endif

size_t allocatedSize = 0;
i32 gcCtr = 0;
i32 objectOverhead = 4;
i32 arrayOverhead = 4 + 4;

int width = 800, height = 600;
double mouseX = width * 0.5, mouseY = height * 0.5;

// imports
void engine_Engine_runAsyncImpl_Lkotlin_jvm_functions_Function0Ljava_lang_StringV(i32, i32) { }
void engine_WebRef2_readBytes_Ljava_lang_StringLjava_lang_ObjectV(i32, i32) { }
void engine_WebRef2_readStream_Ljava_lang_StringLjava_lang_ObjectV(i32, i32) { }
void engine_WebRef2_readText_Ljava_lang_StringLjava_lang_ObjectV(i32, i32) { }
void java_lang_System_gc_V() { gcCtr = 1000000; }
void jvm_GC_markJSReferences_V() { }
void jvm_JVM32_debugArray_Ljava_lang_ObjectV(i32) { }

#include <sstream>
#include <iostream>
std::string strToCpp(i32 addr) {
    if(!addr) return "null";
    i32 chars = r32(addr + 4);
    i32 charLen = r32(chars + 4);
    std::ostringstream os;
    for(i32 i = 0;i<charLen;i++) {
        os << (char) r8(chars + 8 + i);
    }
    return os.str();
}

i32 jvm_JVM32_log_III(i32 code, i32 r) { std::cout << code << ", " << r << std::endl; return r; }
void jvm_JVM32_log_DV(f64 x) { std::cout << x << std::endl; }
void jvm_JVM32_log_ILjava_lang_StringLjava_lang_StringIV(i32 a, i32 b, i32 c, i32 d) { std::cout << a << ", " << strToCpp(b) << ", " << strToCpp(c) << ", " << d << std::endl; }
void jvm_JVM32_log_Ljava_lang_StringCCV(i32 a, i32 b, i32 c) { std::cout << strToCpp(a) << ", " << b << ", " << c << std::endl; }
void jvm_JVM32_log_Ljava_lang_StringDV(i32 a, f64 b) { std::cout << strToCpp(a) << ", " << b << std::endl; }
void jvm_JVM32_log_Ljava_lang_StringIIIV(i32 a, i32 b, i32 c, i32 d) { std::cout << strToCpp(a) << ", " << b << ", " << c << ", " << d << std::endl; }
void jvm_JVM32_log_Ljava_lang_StringIIV(i32 a, i32 b, i32 c) { std::cout << strToCpp(a) << ", " << b << ", " << c << std::endl; }
void jvm_JVM32_log_Ljava_lang_StringILjava_lang_StringV(i32 a, i32 b, i32 c) { std::cout << strToCpp(a) << ", " << b << ", " << strToCpp(c) << std::endl; }
void jvm_JVM32_log_Ljava_lang_StringIV(i32 a, i32 b) { std::cout << strToCpp(a) << ", " << b << std::endl; }
void jvm_JVM32_log_Ljava_lang_StringLjava_lang_StringDV(i32 a, i32 b, f64 c) { std::cout << strToCpp(a) << ", " << strToCpp(b) << ", " << c << std::endl; }
void jvm_JVM32_log_Ljava_lang_StringLjava_lang_StringILjava_lang_StringV(i32, i32, i32, i32) { }
void jvm_JVM32_log_Ljava_lang_StringLjava_lang_StringIV(i32, i32, i32) { }
void jvm_JVM32_log_Ljava_lang_StringLjava_lang_StringJV(i32, i32, i64) { }
void jvm_JVM32_log_Ljava_lang_StringLjava_lang_StringLjava_lang_StringIV(i32, i32, i32, i32) { }
void jvm_JVM32_log_Ljava_lang_StringLjava_lang_StringLjava_lang_StringV(i32, i32, i32) { }
void jvm_JVM32_log_Ljava_lang_StringLjava_lang_StringV(i32, i32) { }
void jvm_JVM32_log_Ljava_lang_StringLjava_lang_StringZV(i32, i32, i32) { }
void jvm_JVM32_log_Ljava_lang_StringV(i32 a) { std::cout << strToCpp(a) << std::endl; }
void jvm_JVM32_trackCalloc_IV(i32) { }
void jvm_JavaLang_printByte_IZV(i32 c, i32 logNotErr) {
    if(logNotErr) std::cout << (char)c;
    else std::cerr << (char)c;
}
void jvm_JavaLang_printFlush_ZV(i32 logNotErr) { 
    if(logNotErr) std::cout << std::endl;
    else std::cerr << std::endl;
}
void jvm_LWJGLxGLFW_disableCursor_V() { }
void jvm_LWJGLxOpenGL_GL11C_glTexImage2D_IIIIIIIIIIV(i32, i32, i32, i32, i32, i32, i32, i32, i32, i32) { }
void jvm_LWJGLxOpenGL_GL11C_glTexImage2D_IIIIIIIIV(i32, i32, i32, i32, i32, i32, i32, i32) { }
void jvm_LWJGLxOpenGL_GL11C_glTexImage3D_IIIIIIIIIIIV(i32, i32, i32, i32, i32, i32, i32, i32, i32, i32, i32) { }
void jvm_LWJGLxOpenGL_GL11C_glTexImage3D_IIIIIIIIIV(i32, i32, i32, i32, i32, i32, i32, i32, i32) { }
void jvm_LWJGLxOpenGL_GL11C_glTexSubImage2D_IIIIIIIIIIV(i32, i32, i32, i32, i32, i32, i32, i32, i32, i32) { }
void jvm_LWJGLxOpenGL_drawBuffersCreate_V() { }
void jvm_LWJGLxOpenGL_drawBuffersExec_V() { }
void jvm_LWJGLxOpenGL_drawBuffersPush_IV(i32) { }
void jvm_LWJGLxOpenGL_glBindAttribLocation2_IILjava_lang_StringV(i32, i32, i32) { }
void jvm_LWJGLxOpenGL_glBufferData16_IIIIV(i32, i32, i32, i32) { }
void jvm_LWJGLxOpenGL_glBufferData8_IIIIV(i32, i32, i32, i32) { }
void jvm_LWJGLxOpenGL_glBufferSubData8_IIIIV(i32, i32, i32, i32) { }
void jvm_LWJGLxOpenGL_glReadPixels_IIIIIIAFV_IIIIIIIIV(i32, i32, i32, i32, i32, i32, i32, i32) { }
void jvm_LWJGLxOpenGL_glUniform1fv_IIIV(i32 u, i32 addr, i32 count) { }
void jvm_LWJGLxOpenGL_glUniform4fv_IIIV(i32 u, i32 addr, i32 count) { }
void jvm_LWJGLxOpenGL_glUniformMatrix4fv_IZIIV(i32, i32, i32, i32) { }
void jvm_LWJGLxOpenGL_glUniformMatrix4x3fv_IZIIV(i32, i32, i32, i32) { }
void jvm_LWJGLxOpenGL_org_lwjgl_opengl_GL11C_glReadPixels_IIIIIIAIV_IIIIIIIIV(i32 a, i32 b, i32 c, i32 d, i32 e, i32 f, i32 g, i32 h) {

}
void me_anno_input_Input_setClipboardContent_Ljava_lang_StringV(i32, i32) { }
void org_lwjgl_glfw_GLFW_glfwSetCursor_JJV(i64, i64) { }
void org_lwjgl_opengl_GL46C_glActiveTexture_IV(i32 x) { glActiveTexture(x); }
void org_lwjgl_opengl_GL46C_glAttachShader_IIV(i32 x, i32 y) { glAttachShader(x,y); }
void org_lwjgl_opengl_GL46C_glBindFramebuffer_IIV(i32 x, i32 y) { glBindFramebuffer(x,y); }
void org_lwjgl_opengl_GL46C_glBindRenderbuffer_IIV(i32 x, i32 y) { glBindRenderbuffer(x,y); }
void org_lwjgl_opengl_GL46C_glBindTexture_IIV(i32 x, i32 y) { glBindTexture(x,y); }
void org_lwjgl_opengl_GL46C_glBindVertexArray_IV(i32 i) { glBindVertexArray(i); }
void org_lwjgl_opengl_GL46C_glBlendEquationSeparate_IIV(i32 a, i32 b) { glBlendEquationSeparate(a,b); }
void org_lwjgl_opengl_GL46C_glBlendFuncSeparate_IIIIV(i32 a, i32 b, i32 c, i32 d) { glBlendFuncSeparate(a,b,c,d); }
void org_lwjgl_opengl_GL46C_glClearColor_FFFFV(f32 r, f32 g, f32 b, f32 a) { glClearColor(r,g,b,a); }
void org_lwjgl_opengl_GL46C_glClearDepth_DV(f64 x) { glClearDepth(x); }
void org_lwjgl_opengl_GL46C_glClear_IV(i32 x) { glClear(x); }
void org_lwjgl_opengl_GL46C_glCompileShader_IV(i32 x) { glCompileShader(x); }
void org_lwjgl_opengl_GL46C_glCullFace_IV(i32 x) { glCullFace(x); }
void org_lwjgl_opengl_GL46C_glDeleteFramebuffers_IV(i32 x) { GLuint xi = x; glDeleteFramebuffers(1, &xi); }
void org_lwjgl_opengl_GL46C_glDeleteProgram_IV(i32 x) { glDeleteProgram(x); }
void org_lwjgl_opengl_GL46C_glDeleteRenderbuffers_IV(i32 x) { GLuint xi = x; glDeleteRenderbuffers(1, &xi); }
void org_lwjgl_opengl_GL46C_glDeleteTextures_IV(i32 x) { GLuint xi = x; glDeleteTextures(1, &xi); }
void org_lwjgl_opengl_GL46C_glDepthFunc_IV(i32 x) { glDepthFunc(x); }
void org_lwjgl_opengl_GL46C_glDepthMask_ZV(i32 x) { glDepthMask(x); }
void org_lwjgl_opengl_GL46C_glDisableVertexAttribArray_IV(i32 x) { glDisableVertexAttribArray(x); }
void org_lwjgl_opengl_GL46C_glDisable_IV(i32 x) { glDisable(x); }
void org_lwjgl_opengl_GL46C_glDrawArraysInstanced_IIIIV(i32 x, i32 y, i32 z, i32 w) { glDrawArraysInstanced(x,y,z,w); }
void org_lwjgl_opengl_GL46C_glDrawArrays_IIIV(i32 x, i32 y, i32 z) { glDrawArrays(x,y,z); }
void org_lwjgl_opengl_GL46C_glDrawElementsInstanced_IIIJIV(i32 x, i32 y, i32 z, i64 w, i32 a) { glDrawElementsInstanced(x,y,z,(void*)w,a); }
void org_lwjgl_opengl_GL46C_glDrawElements_IIIJV(i32 x, i32 y, i32 z, i64 w) { glDrawElements(x,y,z,(void*)w); }
void org_lwjgl_opengl_GL46C_glEnableVertexAttribArray_IV(i32 x) { glEnableVertexAttribArray(x); }
void org_lwjgl_opengl_GL46C_glEnable_IV(i32 x) { glEnable(x); }
void org_lwjgl_opengl_GL46C_glFinish_V() { glFinish(); }
void org_lwjgl_opengl_GL46C_glFlush_V() { glFlush(); }
void org_lwjgl_opengl_GL46C_glFramebufferRenderbuffer_IIIIV(i32 x, i32 y, i32 z, i32 w) { glFramebufferRenderbuffer(x,y,z,w); }
void org_lwjgl_opengl_GL46C_glFramebufferTexture2D_IIIIIV(i32 a, i32 b, i32 c, i32 d, i32 e) { glFramebufferTexture2D(a,b,c,d,e); }
void org_lwjgl_opengl_GL46C_glGenerateMipmap_IV(i32 x) { glGenerateMipmap(x); }
void org_lwjgl_opengl_GL46C_glLinkProgram_IV(i32 x) { glLinkProgram(x); }
void org_lwjgl_opengl_GL46C_glRenderbufferStorage_IIIIV(i32 x, i32 y, i32 z, i32 w) { glRenderbufferStorage(x,y,z,w); }
void org_lwjgl_opengl_GL46C_glScissor_IIIIV(i32 x, i32 y, i32 w, i32 h) { glScissor(x,y,w,h); }
void org_lwjgl_opengl_GL46C_glTexParameterf_IIFV(i32 x, i32 y, f32 z) { glTexParameterf(x,y,z); }
void org_lwjgl_opengl_GL46C_glTexParameteri_IIIV(i32 x, i32 y, i32 z) { glTexParameteri(x,y,z); }
void org_lwjgl_opengl_GL46C_glUniform1f_IFV(i32 a, f32 b) { glUniform1f(a,b); }
void org_lwjgl_opengl_GL46C_glUniform1i_IIV(i32 a, i32 b) { glUniform1i(a,b); }
void org_lwjgl_opengl_GL46C_glUniform2f_IFFV(i32 i, f32 x, f32 y) { glUniform2f(i,x,y); }
void org_lwjgl_opengl_GL46C_glUniform2i_IIIV(i32 i, i32 x, i32 y) { glUniform2i(i,x,y); }
void org_lwjgl_opengl_GL46C_glUniform3f_IFFFV(i32 i, f32 x, f32 y, f32 z) { glUniform3f(i,x,y,z); }
void org_lwjgl_opengl_GL46C_glUniform3i_IIIIV(i32 i, i32 x, i32 y, i32 z) { glUniform3i(i,x,y,z); }
void org_lwjgl_opengl_GL46C_glUniform4f_IFFFFV(i32 i, f32 x, f32 y, f32 z, f32 w) { glUniform4f(i,x,y,z,w); }
void org_lwjgl_opengl_GL46C_glUniform4i_IIIIIV(i32 i, i32 x, i32 y, i32 z, i32 w) { glUniform4i(i,x,y,z,w); }
void org_lwjgl_opengl_GL46C_glUseProgram_IV(i32 x) { glUseProgram(x); }
void org_lwjgl_opengl_GL46C_glVertexAttrib1f_IFV(i32 x, f32 y) { glVertexAttrib1f(x,y); }
void org_lwjgl_opengl_GL46C_glVertexAttribDivisor_IIV(i32 x, i32 y) { glVertexAttribDivisor(x,y); }
void org_lwjgl_opengl_GL46C_glVertexAttribPointer_IIIZIJV(i32 a, i32 b, i32 c, i32 d, i32 e, i64 f) { glVertexAttribPointer(a,b,c,d,e,(void*)f); }
void org_lwjgl_opengl_GL46C_glViewport_IIIIV(i32 x, i32 y, i32 w, i32 h) { glViewport(x,y,w,h); }
void org_lwjgl_opengl_GL11C_glTexParameterfv_IIAFV(i32, i32, i32) { /* todo */ }
void org_lwjgl_opengl_GL15C_glBeginQuery_IIV(i32 a, i32 b) { glBeginQuery(a,b); }
void org_lwjgl_opengl_GL15C_glEndQuery_IV(i32 a) { glEndQuery(a); }
void org_lwjgl_opengl_GL30C_glBindBufferBase_IIIV(i32 a, i32 b, i32 c) { glBindBufferBase(a,b,c); }

// todo handle NaN correctly
i32 dcmpg(f64 a, f64 b) { return (a > b ? 1 : 0) - (a < b ? 1 : 0); }
i32 dcmpl(f64 a, f64 b) { return (a > b ? 1 : 0) - (a < b ? 1 : 0); }
i32 fcmpg(f32 a, f32 b) { return (a > b ? 1 : 0) - (a < b ? 1 : 0); }
i32 fcmpl(f32 a, f32 b) { return (a > b ? 1 : 0) - (a < b ? 1 : 0); }

i32 engine_Engine_fillURL_ACI(i32) { return 0; }
i32 engine_Engine_generateTexture_Ljava_lang_StringLme_anno_gpu_texture_Texture2DLme_anno_utils_async_CallbackV(i32, i32, i32) { return 0; }
i32 engine_TextGen_genASCIITexture_Ljava_lang_StringFIIIIIIFI(i32, f32, i32, i32, i32, i32, i32, i32, f32) { return 0; }
i32 engine_TextGen_genTexTexture_Ljava_lang_StringFLjava_lang_StringIII(i32, f32, i32, i32, i32) { return 0; }
i32 engine_TextGen_measureText1_Ljava_lang_StringFLjava_lang_StringI(i32, f32, i32) { return 0; }
i32 java_io_BufferedInputStream_close_V(i32) { return 0; }
i32 java_io_BufferedInputStream_fill_V(i32) { return 0; }
i32 java_io_InputStreamReader_close_V(i32) { return 0; }
i32 java_io_RandomAccessFile_close0_V(i32) { return 0; }
i32 java_io_RandomAccessFile_open0_Ljava_lang_StringIV(i32, i32, i32) { return 0; }
i32 java_io_RandomAccessFile_seek0_JV(i32, i64) { return 0; }
i32 java_io_RandomAccessFile_writeBytes_ABIIV(i32, i32, i32, i32) { return 0; }
i32 java_lang_Math_round_FI(f32 x) { return (i32) std::round(x); }
f64 java_lang_StrictMath_acos_DD(f64 x) { return std::acos(x); }
f64 java_lang_StrictMath_asin_DD(f64 x) { return std::asin(x); }
f64 java_lang_StrictMath_atan2_DDD(f64 y, f64 x) { return std::atan2(y, x); }
f64 java_lang_StrictMath_atan_DD(f64 x) { return std::atan(x); }
f64 java_lang_StrictMath_cos_DD(f64 x) { return std::cos(x); }
f64 java_lang_StrictMath_cosh_DD(f64 x) { return std::cosh(x); }
f64 java_lang_StrictMath_exp_DD(f64 x) { return std::exp(x); }
f64 java_lang_StrictMath_hypot_DDD(f64 x, f64 y) { return std::hypot(x, y); }
f64 java_lang_StrictMath_log10_DD(f64 x) { return std::log10(x); }
f64 java_lang_StrictMath_log_DD(f64 x) { return std::log(x); }
f64 java_lang_StrictMath_pow_DDD(f64 x, f64 y) { return std::pow(x, y); }
f64 java_lang_StrictMath_sin_DD(f64 x) { return std::sin(x); }
f64 java_lang_StrictMath_sinh_DD(f64 x) { return std::sinh(x); }
f64 java_lang_StrictMath_tan_DD(f64 x) { return std::tan(x); }
i64 java_lang_System_currentTimeMillis_J() {
    auto now = std::chrono::system_clock::now();
    // Convert the time point to milliseconds since the Unix epoch
    auto millis = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
    return millis;
}
i64 java_lang_System_nanoTime_J() { 
     // Get the current time point using steady_clock
    auto time_since_start = std::chrono::steady_clock::now().time_since_epoch();
    // Convert the time point to nanoseconds
    auto time_in_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(time_since_start).count();
    return time_in_ns;
}
i32 java_lang_Thread_setNativeName_Ljava_lang_StringV(i32, i32) { return 0; }
i32 java_lang_Throwable_printStackTrace_V(i32 err) { 
    if(!io(err, 14)) {
        std::cerr << err << " is not a throwable" << std::endl;
        return 0;
    }
    std::string msg = strToCpp(r32(err + objectOverhead));
    std::string name = strToCpp(java_lang_Class_getName_Ljava_lang_String(findClass(rCl(err))).v0);
    std::cerr << name << ": " << msg << std::endl;
    i32 trace = r32(err + objectOverhead + 4);
    if(trace && io(trace, 1)) {
        i32 traceLength = al(trace).v0;
        for(i32 i=0;i<traceLength;i++) {
            i32 element = r32(trace + arrayOverhead + 4 * i);
            if(element && io(element, 15)) {
                std::string clazz = strToCpp(r32(element + objectOverhead));
                std::string name = strToCpp(r32(element + objectOverhead + 4));
                i32 line = r32(element + objectOverhead + 12);
                std::cerr << "  " << clazz << "." << name << ":" << line << std::endl;
            }
        }
    }
    return 0;
}
i32 java_text_SimpleDateFormat_subFormat_IILjava_text_FormatXFieldDelegateLjava_lang_StringBufferZV(i32, i32, i32, i32, i32, i32) { return 0; }
i32 java_util_zip_Deflater_end_JV(i64) { return 0; }
i32 java_util_zip_Deflater_initIDs_V() { return 0; }
i32 java_util_zip_Inflater_end_JV(i64) { return 0; }
i32 java_util_zip_Inflater_initIDs_V() { return 0; }

i32 jvm_JVM32_getAllocatedSize_I() { return allocatedSize; }
i32 jvm_JVM32_grow_IZ(i32 numExtraPages) { 
    size_t extraSize = numExtraPages << 16;
    void* newMemory = realloc(memory, allocatedSize + extraSize);
    if(newMemory) {
        memory = newMemory;
        allocatedSize += extraSize;
        return 1;
    } else return 0;
}

// void org_lwjgl_opengl_GL43C_nglPushDebugGroup_IIIJV(i32, i32, i32, i64) { return 0; }
// void org_lwjgl_opengl_GL46C_glPopDebugGroup_V() { glPopDebugGroup(); return 0; }

i32 jvm_JavaLang_fillD2S_ACDI(i32, f64) { return 0; }
i32 jvm_JavaLang_fillD2S_ACDII(i32, f64, i32) { return 0; }
i32 jvm_JavaX_fillDate_ACI(i32) { return 0; }
f64 jvm_LWJGLxGLFW_getMouseX_D() { return mouseX; }
f64 jvm_LWJGLxGLFW_getMouseY_D() { return mouseY; }
i32 jvm_LWJGLxGLFW_getWindowHeight_I() { return height; }
i32 jvm_LWJGLxGLFW_getWindowWidth_I() { return width; }
i32 jvm_LWJGLxOpenGL_fillProgramInfoLog_ACII(i32, i32) { return 0; }
i32 jvm_LWJGLxOpenGL_fillShaderInfoLog_ACII(i32, i32) { return 0; }
i32 jvm_LWJGLxOpenGL_glGenTexture_I() { GLuint result = 0; glGenTextures(1, &result); return result; }
i32 jvm_LWJGLxOpenGL_glGetUniformLocationString_ILjava_lang_StringI(i32 a, i32 b) { 
    std::string name = strToCpp(b);
    return glGetUniformLocation(a, name.c_str());
}
i32 kotlin_reflect_jvm_KCallablesJvm_setAccessible_Lkotlin_reflect_KCallableZV(i32, i32) { return 0; }
i64 me_anno_ui_debug_JSMemory_jsUsedMemory_J() { return 0; }
i32 me_anno_utils_Sleep_waitUntilOrThrow_ZJLjava_lang_ObjectLkotlin_jvm_functions_Function0V(i32, i64, i32, i32) { return 0; }
i32 new_java_text_SimpleDateFormat_V(i32) { return 0; }
i32 new_kotlin_text_Regex_Ljava_lang_StringV(i32, i32) { return 0; }
i32 org_lwjgl_opengl_GL11C_nglGetFloatv_IJV(i32, i64) { return 0; }
i32 org_lwjgl_opengl_GL11C_nglReadPixels_IIIIIIJV(i32, i32, i32, i32, i32, i32, i64) { return 0; }
i32 org_lwjgl_opengl_GL11C_nglTexImage2D_IIIIIIIIJV(i32, i32, i32, i32, i32, i32, i32, i32, i64) { return 0; }
i32 org_lwjgl_opengl_GL11C_nglTexSubImage2D_IIIIIIIIJV(i32, i32, i32, i32, i32, i32, i32, i32, i64) { return 0; }
i32 org_lwjgl_opengl_GL15C_glBindBuffer_IIV(i32 a, i32 b) { glBindBuffer(a, b); return 0; }
i32 org_lwjgl_opengl_GL15C_glGenBuffers_I() { GLuint result = 0; glGenBuffers(1, &result); return result; }
i32 org_lwjgl_opengl_GL15C_nglBufferSubData_IJJJV(i32, i64, i64, i64) { return 0; }
i32 org_lwjgl_opengl_GL15C_nglDeleteBuffers_IJV(i32, i64) { return 0; }
i32 org_lwjgl_opengl_GL15C_nglGetQueryObjectiv_IIJV(i32, i32, i64) { return 0; }
i32 org_lwjgl_opengl_GL20C_nglDrawBuffers_IJV(i32, i64) { return 0; }
i32 org_lwjgl_opengl_GL20C_nglUniformMatrix2fv_IIZJV(i32, i32, i32, i64) { return 0; }
i32 org_lwjgl_opengl_GL20C_nglUniformMatrix3fv_IIZJV(i32, i32, i32, i64) { return 0; }
i32 org_lwjgl_opengl_GL30C_nglVertexAttribIPointer_IIIIJV(i32, i32, i32, i32, i64) { return 0; }
i32 org_lwjgl_opengl_GL33C_nglGetQueryObjecti64v_IIJV(i32, i32, i64) { return 0; }
i32 org_lwjgl_opengl_GL43C_nglDebugMessageCallback_JJV(i64, i64) { return 0; }
i32 org_lwjgl_opengl_GL45C_nglCreateVertexArrays_IJV(i32, i64) { return 0; }
void org_lwjgl_opengl_GL46C_glBeginQuery_IIV(i32 a, i32 b) { glBeginQuery(a, b); }
void org_lwjgl_opengl_GL46C_glBindBufferBase_IIIV(i32 a, i32 b, i32 c) { glBindBufferBase(a,b,c); }
void org_lwjgl_opengl_GL46C_glBindImageTexture_IIIZIIIV(i32, i32, i32, i32, i32, i32, i32) { }
void org_lwjgl_opengl_GL46C_glBlendEquationSeparatei_IIIV(i32, i32, i32) { }
void org_lwjgl_opengl_GL46C_glBlendFuncSeparatei_IIIIIV(i32, i32, i32, i32, i32) { }
i32 org_lwjgl_opengl_GL46C_glCheckFramebufferStatus_II(i32 i) { return glCheckFramebufferStatus(i); }
void org_lwjgl_opengl_GL46C_glClipControl_IIV(i32 a, i32 b) { glClipControl(a,b); }
i32 org_lwjgl_opengl_GL46C_glCreateProgram_I() { return glCreateProgram(); }
i32 org_lwjgl_opengl_GL46C_glCreateShader_II(i32 type) { return glCreateShader(type); }
void org_lwjgl_opengl_GL46C_glDispatchCompute_IIIV(i32 a, i32 b, i32 c) { glDispatchCompute(a,b,c); }
void org_lwjgl_opengl_GL46C_glEndQuery_IV(i32 x) { glEndQuery(x); }
void org_lwjgl_opengl_GL46C_glFramebufferTextureLayer_IIIIIV(i32 a, i32 b, i32 c, i32 d, i32 e) { glFramebufferTextureLayer(a,b,c,d,e); }
i32 org_lwjgl_opengl_GL46C_glGenFramebuffers_I() { GLuint result = 0; glGenFramebuffers(1, &result); return result; }
i32 org_lwjgl_opengl_GL46C_glGenRenderbuffers_I() { GLuint result = 0; glGenRenderbuffers(1, &result); return result; }
i32 org_lwjgl_opengl_GL46C_glGetError_I() { return glGetError(); }
i32 org_lwjgl_opengl_GL46C_glGetInteger_II(i32 a) { GLint result = 0; glGetIntegeri_v(a, 0, &result); return result; }
i32 org_lwjgl_opengl_GL46C_glGetProgrami_III(i32 a, i32 b) { GLint result = 0; glGetProgramiv(a,b,&result); return result; }
void org_lwjgl_opengl_GL46C_glMemoryBarrier_IV(i32 a) { glMemoryBarrier(a); }
void org_lwjgl_opengl_GL46C_glRenderbufferStorageMultisample_IIIIIV(i32 a, i32 b, i32 c, i32 d, i32 e) { glRenderbufferStorageMultisample(a,b,c,d,e); }
void org_lwjgl_opengl_GL46C_glShaderSource_ILjava_lang_CharSequenceV(i32 a, i32 b) {
    std::string code = strToCpp(b);
    // (GLuint shader, GLsizei count, const GLchar *const* string, const GLint * length);
    GLchar* codePtr = (GLchar*) code.c_str();
    GLint length = code.size();
    glShaderSource(a, 1, &codePtr, &length);
}
i32 org_lwjgl_opengl_GL46C_glTexImage2DMultisample_IIIIIZV(i32 a, i32 b, i32 c, i32 d, i32 e, i32 f) { glTexImage2DMultisample(a,b,c,d,e,f); return 0; }
void org_lwjgl_opengl_GL30C_glRenderbufferStorageMultisample_IIIIIV(i32, i32, i32, i32, i32) {}
void org_lwjgl_opengl_GL32C_glTexImage2DMultisample_IIIIIZV(i32, i32, i32, i32, i32) {}
void org_lwjgl_opengl_GL40C_glBlendEquationSeparatei_IIIV(i32, i32, i32) {}
void org_lwjgl_opengl_GL45C_glClipControl_IIV(i32 a, i32 b) { glClipControl(a,b); }
void org_lwjgl_opengl_GL42C_glBindImageTexture_IIIZIIIV(i32 a, i32 b, i32 c, i32 d, i32 e, i32 f, i32 g) { glBindImageTexture(a,b,c,d!=0,e,f,g); }
void org_lwjgl_opengl_GL43C_glDispatchCompute_IIIV(i32 a, i32 b, i32 c) { glDispatchCompute(a,b,c); }
void org_lwjgl_opengl_GL30C_glFramebufferTextureLayer_IIIIIV(i32 a, i32 b, i32 c, i32 d, i32 e) { glFramebufferTextureLayer(a,b,c,d,e); }
void org_lwjgl_opengl_GL42C_glMemoryBarrier_IV(i32 a) { glMemoryBarrier(a); }
void org_lwjgl_opengl_GL40C_glBlendFuncSeparatei_IIIIIV(i32 a, i32 b, i32 c, i32 d, i32 e) { glBlendFuncSeparatei(a,b,c,d,e); }
void org_lwjgl_opengl_GL30C_glVertexAttribI1i_IIV(i32 a, i32 b) { glVertexAttribI1i(a,b); }

i32 org_lwjgl_system_JNI_callPV_IAIJV(i32, i32, i64) { return 0; }
i32 org_lwjgl_system_JNI_callPV_IIAFJV(i32, i32, i32, i64) { return 0; }
i32 org_lwjgl_system_JNI_callPV_IIIIAIJV(i32, i32, i32, i32, i32, i64) { return 0; }
i32 org_lwjgl_system_JNI_callPV_IIIIIIIIADJV(i32, i32, i32, i32, i32, i32, i32, i32, i32, i64) { return 0; }
i32 org_lwjgl_system_JNI_callPV_IIIIIIIIAFJV(i32, i32, i32, i32, i32, i32, i32, i32, i32, i64) { return 0; }
i32 org_lwjgl_system_JNI_callPV_IIIIIIIIASJV(i32, i32, i32, i32, i32, i32, i32, i32, i32, i64) { return 0; }
i32 org_lwjgl_system_JNI_callPV_IIIIIIIIIAIJV(i32, i32, i32, i32, i32, i32, i32, i32, i32, i32, i64) { return 0; }
i32 org_lwjgl_system_JNI_invokePPV_JIJJV(i64, i32, i64, i64) { return 0; }
i32 org_lwjgl_system_JNI_invokePV_IIJJV(i32, i32, i64, i64) { return 0; }
i32 org_lwjgl_system_JNI_invokePV_IJJV(i32, i64, i64) { return 0; }
i32 org_lwjgl_system_JNI_invokePV_JJV(i64, i64) { return 0; }
i32 static_java_io_BufferedInputStream_V() { return 0; }
i32 static_java_lang_reflect_AccessibleObject_V() { return 0; }
i32 static_java_util_Date_V() { return 0; }
i32 static_java_util_Formatter_V() { return 0; }
i32 static_me_anno_audio_AudioFXCache_V() { return 0; }
i32 static_me_anno_video_formats_gpu_GPUFrame_V() { return 0; }
i32i32 java_io_InputStreamReader_ready_Z(i32) { return { }; }
i32i32 java_lang_ClassLoader_loadClass_Ljava_lang_StringZLjava_lang_Class(i32, i32, i32) { return {}; }
i32i32 java_lang_Class_copyConstructors_ALjava_lang_reflect_ConstructorALjava_lang_reflect_Constructor(i32) { return {}; }
i32i32 java_lang_Class_getDeclaredConstructors0_ZALjava_lang_reflect_Constructor(i32, i32) { return { }; }
i32i32 java_lang_Class_getGenericInterfaces_ALjava_lang_reflect_Type(i32) { return { }; }
i32i32 java_lang_Class_getModifiers_I(i32) { return { }; }
i32i32 java_lang_Class_getName0_Ljava_lang_String(i32) { return { }; }
i32i32 java_lang_Class_isInterface_Z(i32) { return { }; }
f64i32 java_lang_Double_parseDouble_Ljava_lang_StringD(i32) { return { }; }
f32i32 java_lang_Float_parseFloat_Ljava_lang_StringF(i32) { return { }; }
i32i32 java_lang_Thread_holdsLock_Ljava_lang_ObjectZ(i32) { return { }; }
i32i32 java_lang_Thread_isAlive_Z(i32) { return { }; }
i32i32 java_lang_Throwable_getStackTraceElement_ILjava_lang_StackTraceElement(i32, i32) { return { }; }
i32i32 java_lang_reflect_Field_acquireFieldAccessor_ZLsun_reflect_FieldAccessor(i32, i32) { return { }; }
i32i32 java_text_DateFormatSymbols_getProviderInstance_Ljava_util_LocaleLjava_text_DateFormatSymbols(i32) { return { }; }
i32i32 java_text_DecimalFormatSymbols_getInstance_Ljava_util_LocaleLjava_text_DecimalFormatSymbols(i32) { return { }; }
i32i32 java_text_NumberFormat_getInstance_Ljava_util_LocaleILjava_text_NumberFormat(i32, i32) { return { }; }
i32i32 java_util_Date_clone_Ljava_lang_Object(i32) { return { }; }
i64i32 java_util_Date_getMillisOf_Ljava_util_DateJ(i32) { return { }; }
i64i32 java_util_Date_getTimeImpl_J(i32) { return { }; }
i32i32 java_util_Date_toString_Ljava_lang_String(i32) { return { }; }
i32i32 java_util_Formatter_parse_Ljava_lang_StringALjava_util_FormatterXFormatString(i32, i32) { return { }; }
i32i32 java_util_Properties_isEmpty_Z(i32) { return { }; }
i32i32 java_util_zip_Deflater_deflateBytes_JABIIII(i32, i64, i32, i32, i32, i32) { return { }; }
i64i32 java_util_zip_Deflater_init_IIZJ(i32, i32, i32) { return { }; }
i32i32 java_util_zip_Inflater_inflateBytes_JABIII(i32, i64, i32, i32, i32) { return { }; }
i64i32 java_util_zip_Inflater_init_ZJ(i32) { return { }; }
i32i32 jvm_custom_File_isAbsolute_Z(i32) { return { }; }
i32i32 kotlin_random_jdk8_PlatformThreadLocalRandom_getImpl_Ljava_util_Random(i32) { return { }; }
i32i32 kotlin_random_jdk8_PlatformThreadLocalRandom_nextInt_III(i32, i32, i32) { return { }; }
// i32i32 kotlin_reflect_full_KClasses_getMemberFunctions_Lkotlin_reflect_KClassLjava_util_Collection(i32) { return { }; }
i32i32 kotlin_reflect_full_KClasses_getMemberProperties_Lkotlin_reflect_KClassLjava_util_Collection(i32) { return { }; }
i32i32 kotlin_reflect_jvm_ReflectJvmMapping_getJavaMethod_Lkotlin_reflect_KFunctionLjava_lang_reflect_Method(i32) { return { }; }
i32i32 kotlin_text_Regex_matches_Ljava_lang_CharSequenceZ(i32, i32) { return { }; }
i32i32 kotlin_text_Regex_toString_Ljava_lang_String(i32) { return { }; }
i32i32 me_anno_ecs_prefab_change_PathXCompanion_generateRandomId_Ljava_lang_String(i32) { return { }; }
f64i32 me_anno_maths_Maths_random_D() { return { }; }
i64i32 org_lwjgl_system_JNI_invokePP_JIIJJ(i64, i32, i32, i64) { return { }; }
i32i32 sun_reflect_Reflection_getClassAccessFlags_Ljava_lang_ClassI(i32) { return { }; }
i64i32 me_anno_input_Input_initForGLFWXlambdaX1_Lme_anno_gpu_OSWindowJIJVxd26ac3d_address_J(i32) { return {}; }
i64i32 me_anno_input_Input_initForGLFWXlambdaX2_Lme_anno_gpu_OSWindowJIIVxd26ac3d_address_J(i32) { return {}; }
i64i32 me_anno_input_Input_initForGLFWXlambdaX3_Lme_anno_gpu_OSWindowJDDVxd26ac3d_address_J(i32) { return {}; }
i64i32 me_anno_input_Input_initForGLFWXlambdaX4_Lme_anno_gpu_OSWindowJIIIVxd26ac3d_address_J(i32) { return {}; }
i64i32 me_anno_input_Input_initForGLFWXlambdaX5_Lme_anno_gpu_OSWindowJDDVxd26ac3d_address_J(i32) { return {}; }
i64i32 me_anno_input_Input_initForGLFWXlambdaX6_Lme_anno_gpu_OSWindowJIIIIVxd26ac3d_address_J(i32) { return {}; }
i32i32 me_anno_ui_editor_stacked_StackPanel_getValue_Ljava_lang_Object(i32) { return {}; }
i32i32 me_anno_ui_editor_stacked_StackPanel_setValue_Ljava_lang_ObjectIZLme_anno_ui_Panel(i32, i32, i32, i32) { return {}; }
i32i32 me_anno_ui_input_NumberInput_getValue_Ljava_lang_Object(i32) { return {}; }
i32i32 me_anno_ui_input_NumberInput_setValue_Ljava_lang_ObjectIZLme_anno_ui_Panel(i32, i32, i32, i32) { return {}; }
i64i32 me_anno_gpu_WindowManagement_runRenderLoop0XlambdaX5_IIIIIJJVxv1311c2a2_address_J(i32) { return {}; }
i32i32 java_util_TreeMapXPrivateEntryIterator_next_Ljava_lang_Object(i32) { return {}; }
i32i32 java_util_WeakHashMapXHashIterator_next_Ljava_lang_Object(i32) { return {}; }
i32i32 kotlin_collections_CharIterator_hasNext_Z(i32) { return {}; }
i32 me_anno_io_base_BaseReader_readAllInList_V(i32) { return 0; }
i32i32 kotlin_jvm_internal_MutablePropertyReference_getGetter_Lkotlin_reflect_KPropertyXGetter(i32) { return {}; }
i32i32 kotlin_jvm_internal_PropertyReference_getGetter_Lkotlin_reflect_KPropertyXGetter(i32) { return {}; }

GLFWwindow* window = nullptr;
void jvm_LWJGLxGLFW_setTitle_Ljava_lang_StringV(i32 str) {
    std::string string = strToCpp(str);
    glfwSetWindowTitle(window, string.c_str());
}

void unreachable(std::string msg) {
    std::cout << "!! Unreachable(" << msg << ") !!" << std::endl;
    // create new throwable, and return error
    i32 err = cr(14).v0;
    new_java_lang_Throwable_V(err);
    java_lang_Throwable_printStackTrace_V(err);
    exit(-1);
}

void initMemory() {

    size_t baseSizeInBlocks = (global_G0 >> 16) + 10; // 44 are requested
    allocatedSize = baseSizeInBlocks << 16;

    // allocate memory
    memory = calloc(allocatedSize, 1);
    if(!memory) {
        std::cerr << "Failed allocating initial memory" << std::endl;
        allocatedSize = 0;
        return;
    }

    // go through folder, and load all related memory blocks
    std::string path = "./data";
    std::string prefix = "jvm2wasm-data-";
    std::string suffix = ".bin";
    for (const auto& entry : std::filesystem::directory_iterator(path)) {
        auto pathI = entry.path();
        std::string pathName = pathI.filename().string();
        if(pathName.starts_with(prefix) && pathName.ends_with(suffix)) {
            size_t separator = pathName.find('-', prefix.length());
            if(separator < 0) continue;
            std::string startS = pathName.substr(prefix.length(), separator - prefix.length());
            std::string endS = pathName.substr(separator+1, pathName.length()-suffix.length()-(separator+1));
            if(startS.length() < 1 || endS.length() < startS.length()) continue;
            size_t startI = std::stol(startS);
            size_t endI = std::stol(endS);
            if(startI < 0 || endI <= startI || endI > allocatedSize) continue;
            if(std::filesystem::file_size(pathI) != endI - startI) continue;
            std::ifstream file(pathI, std::ios::binary);
            file.read(((char*) memory) + startI, endI - startI);
            if(!file) std::cerr << "Failed reading file " << pathName << std::endl;
            std::cout << "[FS] Loaded " << startI << " - " << endI << " into memory" << std::endl;
        }
    }


}

void createWindow() {
    if(!glfwInit()) {
        std::cerr << "Failed to initialize GLFW" << std::endl;
        return;
    }
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    //glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
    std::cout << "Creating window " << width << " x " << height << std::endl;
    window = glfwCreateWindow(width, height, "Rem's Engine", nullptr, nullptr);
    if (window == nullptr) {
        std::cerr << "Failed to create GLFW window" << std::endl;
        glfwTerminate();
        return;
    }
    glfwMakeContextCurrent(window);
    // todo continue
}

// Linux:
// g++ -std=c++20 jvm2wasm.cpp -fmax-errors=20

int main() {

    initMemory();
    initFunctionTable();

    createWindow();
    if (!window) return -1;
    if (!gladLoadGL((GLADloadfunc) glfwGetProcAddress)) {
        std::cerr << "Failed to initialize GLAD" << std::endl;
        glfwTerminate();
        return -2;
    }

    bool supportsFP16 = false;
    bool supportsFP32 = true;
    i32 err = 0;
    err = init();
    if(err != 0) {
        java_lang_Throwable_printStackTrace_V(err);
        return -3;
    }
    err = engine_Engine_main_Ljava_lang_StringZZV(0, supportsFP16, supportsFP32);
    if(err != 0) {
        java_lang_Throwable_printStackTrace_V(err);
        return -4;
    }

    // while not done,
    float x = 1, dt = 1.0 / 60.0;
    while(!glfwWindowShouldClose(window)) {
        glfwPollEvents();

        glfwGetCursorPos(window, &mouseX, &mouseY);

        glViewport(0, 0, width, height);
        glClearColor(x,x,x,1);
        glClear(GL_COLOR_BUFFER_BIT);

        x *= 0.99;
        if(x < 0.05) x = 1.0;

        err = engine_Engine_update_IIFV(width, height, dt);
        if(err != 0) {
            java_lang_Throwable_printStackTrace_V(err);
            break;
        }

        if(gcCtr++ > 200) {
            std::cout << "Running GC" << std::endl;
            gc();
            gcCtr = 0;
        }

        // todo run graphics
        // todo if GC timer reaches X, run GC

        glfwSwapBuffers(window);
    }

    std::cout << "Closing" << std::endl;
    glfwTerminate();

    return 0;
}