var lib = {
  // Implemented using @JavaScript(code="..."):
  engine_Engine_fillURL_ACI(arg0) {var loc = window.location.href;
var dir = loc.substring(0, loc.lastIndexOf('/'));return fill(arg0,dir+'/assets/')},
  engine_Engine_generateTexture_Ljava_lang_StringLme_anno_gpu_texture_Texture2DLkotlin_jvm_functions_Function1V(arg0,arg1,arg2) {var img = new Image();
jsRefs[arg1]=1;
jsRefs[arg2]=1;
img.onload=function(){
   var w=img.width,h=img.height;
   var canvas=document.createElement('canvas')
   canvas.width=w;canvas.height=h;
   var ctx=canvas.getContext('2d')
   ctx.drawImage(img,0,0,w,h);
   var x=window.lib.prepareTexture(arg1);
   if(x) throw x;
   gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,ctx.getImageData(0,0,w,h).data);
   x=window.lib.finishTexture(arg1,w,h,arg2);
   if(x) throw x;
   delete jsRefs[arg1];
   delete jsRefs[arg2];}
lib.onerror=function(){
   var x=window.lib.finishTexture(0,-1,-1,arg2);
   if(x) throw x;
   delete jsRefs[arg1];
   delete jsRefs[arg2];
}
img.src = str(arg0);
},
  engine_TextGen_genTexTexture_Ljava_lang_StringFLjava_lang_StringIII(arg0,arg1,arg2,arg3,arg4) {arg2=str(arg2);
measureText(arg0,arg1,arg2);
var w=Math.max(1,Math.min(arg3,Math.ceil(tmp.width))),h=arg4;
txtCanvas.width=w;txtCanvas.height=h;
ctx.fillStyle='#000'
ctx.fillRect(0,0,w,h)
ctx.fillStyle='#fff'
ctx.textAlign='center'
ctx.font=(arg1|0)+'px '+str(arg0);
ctx.fillText(arg2,w/2,1+h/1.3);
gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,ctx.getImageData(0,0,w,h).data);
return w;},
  engine_TextGen_measureText1_Ljava_lang_StringFLjava_lang_StringI(arg0,arg1,arg2) {measureText(arg0,arg1,str(arg2));return Math.ceil(tmp.width)},
  engine_WebRef2_readStream_Ljava_lang_StringLjava_lang_ObjectV(arg0,arg1) {var x = new XMLHttpRequest();
var url = str(arg0);
var args={};
jsRefs[arg1]=1;
x.open('POST', url);
x.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');
x.onreadystatechange = function(){
   if(x.readyState==4){
       if(x.status==200){           var data = x.response;
           var dst = window.lib.createBytes(data.length);
           if(dst[1]) throw dst[1];
           dst = dst[0];
           var dst2 = window.lib.createByteStream(dst);
           if(dst2[1]) throw dst2[1];
           dst2 = dst2[0];
           const byteArray = new Uint8Array(data);
           byteArray.forEach((element, index) => {
               window.lib.w8(dst+arrayOverhead+index, element);
           });
           window.lib.invoke2(arg1, dst2, 0);       } else {
           window.lib.invoke2(arg1, 0, 0);       }
       delete jsRefs[arg1];
   }
}
x.send(args);},
  java_lang_Math_cos_DD(arg0) {return Math.cos(arg0);},
  java_lang_Math_exp_DD(arg0) {return Math.exp(arg0);},
  java_lang_Math_hypot_DDD(arg0,arg1) {return Math.hypot(arg0,arg1);},
  java_lang_Math_log_DD(arg0) {return Math.log(arg0);},
  java_lang_Math_pow_DDD(arg0,arg1) {return Math.pow(arg0,arg1);},
  java_lang_Math_round_FI(arg0) {return Math.round(arg0)},
  java_lang_Math_sin_DD(arg0) {return Math.sin(arg0);},
  java_lang_StrictMath_cos_DD(arg0) {return Math.cos(arg0);},
  java_lang_StrictMath_exp_DD(arg0) {return Math.exp(arg0);},
  java_lang_StrictMath_hypot_DDD(arg0,arg1) {return Math.hypot(arg0,arg1);},
  java_lang_StrictMath_log_DD(arg0) {return Math.log(arg0);},
  java_lang_StrictMath_pow_DDD(arg0,arg1) {return Math.pow(arg0,arg1);},
  java_lang_StrictMath_sin_DD(arg0) {return Math.sin(arg0);},
  java_lang_System_currentTimeMillis_J() {return BigInt(Date.now());},
  java_lang_System_nanoTime_J() {return BigInt(Math.round(performance.now()*1e6));},
  java_lang_Throwable_printStackTrace_V(arg0) {trace(arg0)},
  jvm_GC_markJSReferences_V() {markJSReferences()},
  jvm_JVM32_getAllocatedSize_I() {return memory.buffer.byteLength;},
  jvm_JVM32_grow_IZ(arg0) {console.log('Growing by ' + (arg0<<6) + ' kiB, total: '+(memory.buffer.byteLength>>20)+' MiB'); try { memory.grow(arg0); return true; } catch(e) { console.error(e.stack); return false; }},
  jvm_JVM32_log_DV(arg0) {console.log(arg0);},
  jvm_JVM32_log_III(arg0,arg1) {console.log(arg0, arg1); return arg1;},
  jvm_JVM32_log_ILjava_lang_StringLjava_lang_StringIV(arg0,arg1,arg2,arg3) {console.log(arg0, str(arg1), str(arg2), arg3);},
  jvm_JVM32_log_Ljava_lang_StringDV(arg0,arg1) {console.log(str(arg0), arg1);},
  jvm_JVM32_log_Ljava_lang_StringIIIV(arg0,arg1,arg2,arg3) {console.log(str(arg0), arg1, arg2, arg3);},
  jvm_JVM32_log_Ljava_lang_StringIIV(arg0,arg1,arg2) {console.log(str(arg0), arg1, arg2);},
  jvm_JVM32_log_Ljava_lang_StringILjava_lang_StringV(arg0,arg1,arg2) {console.log(str(arg0), arg1, str(arg2));},
  jvm_JVM32_log_Ljava_lang_StringIV(arg0,arg1) {console.log(str(arg0), arg1);},
  jvm_JVM32_log_Ljava_lang_StringLjava_lang_StringIV(arg0,arg1,arg2) {console.log(str(arg0), str(arg1), arg2);},
  jvm_JVM32_log_Ljava_lang_StringLjava_lang_StringLjava_lang_StringIV(arg0,arg1,arg2,arg3) {console.log(str(arg0), str(arg1), str(arg2), arg3);},
  jvm_JVM32_log_Ljava_lang_StringLjava_lang_StringLjava_lang_StringV(arg0,arg1,arg2) {console.log(str(arg0), str(arg1), str(arg2));},
  jvm_JVM32_log_Ljava_lang_StringLjava_lang_StringV(arg0,arg1) {console.log(str(arg0), str(arg1));},
  jvm_JVM32_log_Ljava_lang_StringV(arg0) {console.log(str(arg0));},
  jvm_JVM32_throwJs_IV(arg0) {throw arg0},
  jvm_JVM32_throwJs_Ljava_lang_StringIIIV(arg0,arg1,arg2,arg3) {throw [str(arg0),arg1,arg2,arg3]},
  jvm_JVM32_throwJs_Ljava_lang_StringIIV(arg0,arg1,arg2) {throw [str(arg0),arg1,arg2]},
  jvm_JVM32_throwJs_Ljava_lang_StringIV(arg0,arg1) {throw [str(arg0),arg1]},
  jvm_JVM32_throwJs_Ljava_lang_StringV(arg0) {throw str(arg0)},
  jvm_JVM32_throwJs_V() {throw 'Internal VM error!'},
  jvm_JVM32_trackCalloc_IV(arg0) {calloc[arg0] = (calloc[arg0]||0)+1},
  jvm_JavaLang_fillD2S_ACDI(arg0,arg1) {var s=arg1+'';if(s.indexOf('.')<0)s+='.0';return fill(arg0,s)},
  jvm_JavaLang_fillD2S_ACDII(arg0,arg1,arg2) {return fill(arg0, arg1.toFixed(arg2))},
  jvm_JavaLang_printByte_IZV(arg0,arg1) {commandLine[arg1].push(String.fromCharCode(arg0))},
  jvm_JavaLang_printFlush_ZV(arg0) {var c=commandLine[arg0];if(c.length>0){if(!arg0)ec++;(arg0?console.log:console.error)(c.join('')); commandLine[arg0] = []}},
  jvm_JavaUtil_seedUniquifier_D() {return Math.random()},
  jvm_JavaX_fillDate_ACI(arg0) {var d = new Date();var h=d.getHours(),m=d.getMinutes(),s=d.getSeconds();return fill(arg0, (h<10?'0'+h:h)+':'+(m<10?'0'+m:m)+':'+(s<10?'0'+s:s))},
  jvm_LWJGLxGLFW_disableCursor_V() {canvas.requestPointerLock = canvas.requestPointerLock || canvas.mozRequestPointerLock;
canvas.requestPointerLock()},
  jvm_LWJGLxGLFW_getMouseX_D() {return mouseX},
  jvm_LWJGLxGLFW_getMouseY_D() {return mouseY},
  jvm_LWJGLxGLFW_getWindowHeight_I() {return canvas.height},
  jvm_LWJGLxGLFW_getWindowWidth_I() {return canvas.width},
  jvm_LWJGLxGLFW_setTitle_Ljava_lang_StringV(arg0) {document.title=str(arg0)},
  jvm_LWJGLxOpenGL_GL11C_glTexImage2D_IIIIIIIIIIV(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8,arg9) {gl.texImage2D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,   arg7 == gl.UNSIGNED_INT ?   new Uint32Array(memory.buffer, arg8, arg9>>2):   arg7 == gl.FLOAT ?   new Float32Array(memory.buffer, arg8, arg9>>2):   new Uint8Array(memory.buffer, arg8, arg9))},
  jvm_LWJGLxOpenGL_GL11C_glTexImage2D_IIIIIIIIV(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7) {gl.texImage2D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,null)},
  jvm_LWJGLxOpenGL_GL11C_glTexImage3D_IIIIIIIIIIIV(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8,arg9,arg10) {gl.texImage3D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8,   arg8 == gl.UNSIGNED_INT ?   new Uint32Array(memory.buffer, arg9, arg10>>2):   arg8 == gl.FLOAT ?   new Float32Array(memory.buffer, arg9, arg10>>2):   new Uint8Array(memory.buffer, arg9, arg10))},
  jvm_LWJGLxOpenGL_GL11C_glTexImage3D_IIIIIIIIIV(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8) {gl.texImage3D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8,null)},
  jvm_LWJGLxOpenGL_GL11C_glTexSubImage2D_IIIIIIIIIIV(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8,arg9) {gl.texSubImage2D(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,   arg7 == gl.UNSIGNED_INT ?   new Uint32Array(memory.buffer, arg8, arg9>>2):   arg7 == gl.FLOAT ?   new Float32Array(memory.buffer, arg8, arg9>>2):   new Uint8Array(memory.buffer, arg8, arg9))},
  jvm_LWJGLxOpenGL_GL20C_glGetUniformLocation2_ILjava_lang_CharSequenceI(arg0,arg1) {return map(gl.getUniformLocation(unmap(arg0), str(arg1)))},
  jvm_LWJGLxOpenGL_drawBuffersCreate_V() {window.tmp=[]},
  jvm_LWJGLxOpenGL_drawBuffersExec_V() {gl.drawBuffers(window.tmp);delete window.tmp},
  jvm_LWJGLxOpenGL_drawBuffersPush_IV(arg0) {window.tmp.push(arg0)},
  jvm_LWJGLxOpenGL_fillProgramInfoLog_ACII(arg0,arg1) {return fill(arg0, gl.getProgramInfoLog(unmap(arg1)))},
  jvm_LWJGLxOpenGL_fillShaderInfoLog_ACII(arg0,arg1) {return fill(arg0, gl.getShaderInfoLog(unmap(arg1)))},
  jvm_LWJGLxOpenGL_glBindAttribLocation2_IILjava_lang_StringV(arg0,arg1,arg2) {gl.bindAttribLocation(unmap(arg0),arg1,str(arg2))},
  jvm_LWJGLxOpenGL_glBufferData8_IIIIV(arg0,arg1,arg2,arg3) {gl.bufferData(arg0,new Uint8Array(memory.buffer,arg1,arg2),arg3)},
  jvm_LWJGLxOpenGL_glBufferSubData8_IIIIV(arg0,arg1,arg2,arg3) {gl.bufferSubData(arg0,arg1,new Uint8Array(memory.buffer,arg2,arg3))},
  jvm_LWJGLxOpenGL_glGenTexture_I() {return map(gl.createTexture())},
  jvm_LWJGLxOpenGL_glUniform4fv_IIIV(arg0,arg1,arg2) {gl.uniform4fv(unmap(arg0), new Float32Array(memory.buffer, arg1, arg2))},
  jvm_LWJGLxOpenGL_glUniformMatrix4fv_IZIIV(arg0,arg1,arg2,arg3) {gl.uniformMatrix4fv(unmap(arg0), arg1, new Float32Array(memory.buffer, arg2, arg3))},
  me_anno_input_Input_setClipboardContent_Ljava_lang_StringV(arg0,arg1) {if(arg1) navigator.clipboard.writeText(str(arg1))},
  me_anno_ui_debug_JSMemory_jsUsedMemory_J() {try { return BigInt(window.performance.memory.usedJSHeapSize); } catch(e){ return 0n; }},
  org_lwjgl_glfw_GLFW_glfwSetCursor_JJV(arg0,arg1) {},
  org_lwjgl_opengl_GL11C_glClearColor_FFFFV(arg0,arg1,arg2,arg3) {gl.clearColor(arg0,arg1,arg2,arg3)},
  org_lwjgl_opengl_GL11C_glClearDepth_DV(arg0) {gl.clearDepth(arg0)},
  org_lwjgl_opengl_GL11C_glClear_IV(arg0) {gl.clear(arg0)},
  org_lwjgl_opengl_GL11C_glDeleteTextures_IV(arg0) {gl.deleteTexture(unmap(arg0)); delete glMap[arg0];},
  org_lwjgl_opengl_GL11C_glDisable_IV(arg0) {gl.disable(arg0)},
  org_lwjgl_opengl_GL11C_glDrawBuffer_IV(arg0) {gl.drawBuffers([arg0])},
  org_lwjgl_opengl_GL11C_glEnable_IV(arg0) {gl.enable(arg0)},
  org_lwjgl_opengl_GL11C_glFinish_V() {gl.finish()},
  org_lwjgl_opengl_GL11C_glGetError_I() {return gl.getError()},
  org_lwjgl_opengl_GL11C_glGetInteger_II(arg0) {if(arg0 == 0x826E) return 1024; return gl.getParameter(arg0)},
  org_lwjgl_opengl_GL11C_glViewport_IIIIV(arg0,arg1,arg2,arg3) {gl.viewport(arg0,arg1,arg2,arg3)},
  org_lwjgl_opengl_GL15C_glBindBuffer_IIV(arg0,arg1) {gl.bindBuffer(arg0,unmap(arg1))},
  org_lwjgl_opengl_GL15C_glGenBuffers_I() {return map(gl.createBuffer())},
  org_lwjgl_opengl_GL20C_glAttachShader_IIV(arg0,arg1) {gl.attachShader(unmap(arg0), unmap(arg1))},
  org_lwjgl_opengl_GL20C_glCompileShader_IV(arg0) {gl.compileShader(unmap(arg0))},
  org_lwjgl_opengl_GL20C_glCreateProgram_I() {return map(gl.createProgram())},
  org_lwjgl_opengl_GL20C_glCreateShader_II(arg0) {return map(gl.createShader(arg0))},
  org_lwjgl_opengl_GL20C_glDeleteProgram_IV(arg0) {gl.deleteProgram(unmap(arg0))},
  org_lwjgl_opengl_GL20C_glDisable_IV(arg0) {gl.disable(arg0)},
  org_lwjgl_opengl_GL20C_glEnable_IV(arg0) {gl.enable(arg0)},
  org_lwjgl_opengl_GL20C_glGetError_I() {return gl.getError()},
  org_lwjgl_opengl_GL20C_glLinkProgram_IV(arg0) {gl.linkProgram(unmap(arg0))},
  org_lwjgl_opengl_GL20C_glShaderSource_ILjava_lang_CharSequenceV(arg0,arg1) {gl.shaderSource(unmap(arg0),str(arg1).split('#extension').join('// #ext'))},
  org_lwjgl_opengl_GL20C_glUniform1f_IFV(arg0,arg1) {gl.uniform1f(unmap(arg0),arg1)},
  org_lwjgl_opengl_GL20C_glUniform1i_IIV(arg0,arg1) {gl.uniform1i(unmap(arg0),arg1)},
  org_lwjgl_opengl_GL20C_glUniform2f_IFFV(arg0,arg1,arg2) {gl.uniform2f(unmap(arg0),arg1,arg2)},
  org_lwjgl_opengl_GL20C_glUniform2i_IIIV(arg0,arg1,arg2) {gl.uniform2i(unmap(arg0),arg1,arg2)},
  org_lwjgl_opengl_GL20C_glUniform3f_IFFFV(arg0,arg1,arg2,arg3) {gl.uniform3f(unmap(arg0),arg1,arg2,arg3)},
  org_lwjgl_opengl_GL20C_glUniform4f_IFFFFV(arg0,arg1,arg2,arg3,arg4) {gl.uniform4f(unmap(arg0),arg1,arg2,arg3,arg4)},
  org_lwjgl_opengl_GL20C_glUseProgram_IV(arg0) {gl.useProgram(unmap(arg0))},
  org_lwjgl_opengl_GL20C_glVertexAttribPointer_IIIZIJV(arg0,arg1,arg2,arg3,arg4,arg5) {gl.vertexAttribPointer(arg0,arg1,arg2,!!arg3,arg4,Number(arg5))},
  org_lwjgl_opengl_GL30C_glBindFramebuffer_IIV(arg0,arg1) {gl.bindFramebuffer(arg0,unmap(arg1))},
  org_lwjgl_opengl_GL30C_glBindVertexArray_IV(arg0) {gl.bindVertexArray(unmap(arg0))},
  org_lwjgl_opengl_GL30C_glBlendEquationSeparate_IIV(arg0,arg1) {gl.blendEquationSeparate(arg0,arg1)},
  org_lwjgl_opengl_GL30C_glBlendFuncSeparate_IIIIV(arg0,arg1,arg2,arg3) {gl.blendFuncSeparate(arg0,arg1,arg2,arg3)},
  org_lwjgl_opengl_GL30C_glCheckFramebufferStatus_II(arg0) {return gl.checkFramebufferStatus(arg0)},
  org_lwjgl_opengl_GL30C_glDeleteFramebuffers_IV(arg0) {gl.deleteFramebuffer(unmap(arg0)); delete glMap[arg0];},
  org_lwjgl_opengl_GL30C_glDeleteRenderbuffers_AIV(arg0) {gl.deleteRenderbuffer(arg0);},
  org_lwjgl_opengl_GL30C_glDisable_IV(arg0) {gl.disable(arg0)},
  org_lwjgl_opengl_GL30C_glDrawBuffer_IV(arg0) {gl.drawBuffers([arg0])},
  org_lwjgl_opengl_GL30C_glEnable_IV(arg0) {gl.enable(arg0)},
  org_lwjgl_opengl_GL30C_glFramebufferTexture2D_IIIIIV(arg0,arg1,arg2,arg3,arg4) {gl.framebufferTexture2D(arg0,arg1,arg2,unmap(arg3),arg4)},
  org_lwjgl_opengl_GL30C_glGenFramebuffers_I() {return map(gl.createFramebuffer())},
  org_lwjgl_opengl_GL30C_glGetError_I() {return gl.getError()},
  org_lwjgl_opengl_GL30C_glTexParameteri_IIIV(arg0,arg1,arg2) {if(arg1!=33169) gl.texParameteri(arg0,arg1,arg2)},
  org_lwjgl_opengl_GL33C_glDisableVertexAttribArray_IV(arg0) {gl.disableVertexAttribArray(arg0)},
  org_lwjgl_opengl_GL33C_glDrawArrays_IIIV(arg0,arg1,arg2) {gl.drawArrays(arg0,arg1,arg2)},
  org_lwjgl_opengl_GL33C_glEnableVertexAttribArray_IV(arg0) {gl.enableVertexAttribArray(arg0)},
  org_lwjgl_opengl_GL33C_glVertexAttribDivisor_IIV(arg0,arg1) {gl.vertexAttribDivisor(arg0,arg1)},
  org_lwjgl_opengl_GL45C_glActiveTexture_IV(arg0) {gl.activeTexture(arg0)},
  org_lwjgl_opengl_GL45C_glBindTexture_IIV(arg0,arg1) {if(arg0 == 37120) arg0 = 3553; gl.bindTexture(arg0,unmap(arg1))},
  org_lwjgl_opengl_GL45C_glCullFace_IV(arg0) {gl.cullFace(arg0)},
  org_lwjgl_opengl_GL45C_glDepthFunc_IV(arg0) {gl.depthFunc(arg0)},
  org_lwjgl_opengl_GL45C_glDepthMask_ZV(arg0) {gl.depthMask(!!arg0)},
  org_lwjgl_opengl_GL45C_glDepthRange_DDV(arg0,arg1) {gl.depthRange(arg0,arg1)},
  org_lwjgl_opengl_GL45C_glDisable_IV(arg0) {gl.disable(arg0)},
  org_lwjgl_opengl_GL45C_glEnable_IV(arg0) {gl.enable(arg0)},
  org_lwjgl_opengl_GL45C_glGenerateMipmap_IV(arg0) {gl.generateMipmap(arg0)},
  org_lwjgl_opengl_GL45C_glTexParameterf_IIFV(arg0,arg1,arg2) {gl.texParameterf(arg0,arg1,arg2)},
  org_lwjgl_opengl_GL45C_glTexParameteri_IIIV(arg0,arg1,arg2) {if(arg1!=33169) gl.texParameteri(arg0,arg1,arg2)},
  
  // Not properly implemented:
  java_awt_GraphicsEnvironment_getAvailableFontFamilyNames_Ljava_util_LocaleALjava_lang_String(arg0) { throw ('java_awt_GraphicsEnvironment_getAvailableFontFamilyNames_Ljava_util_LocaleALjava_lang_String not implemented'); },
  java_awt_GraphicsEnvironment_getDefaultScreenDevice_Ljava_awt_GraphicsDevice() { throw ('java_awt_GraphicsEnvironment_getDefaultScreenDevice_Ljava_awt_GraphicsDevice not implemented'); },
  java_awt_GraphicsEnvironment_registerFont_Ljava_awt_FontZ(arg0) { throw ('java_awt_GraphicsEnvironment_registerFont_Ljava_awt_FontZ not implemented'); },
  java_io_Writer_write_ACIIV(arg0,arg1,arg2) { throw ('java_io_Writer_write_ACIIV not implemented'); },
  java_lang_ClassLoader_loadClass_Ljava_lang_StringZLjava_lang_Class(arg0,arg1) { throw ('java_lang_ClassLoader_loadClass_Ljava_lang_StringZLjava_lang_Class not implemented'); },
  java_lang_Class_getComponentType_Ljava_lang_Class() { throw ('java_lang_Class_getComponentType_Ljava_lang_Class not implemented'); },
  java_lang_Class_getGenericInterfaces_ALjava_lang_reflect_Type() { throw ('java_lang_Class_getGenericInterfaces_ALjava_lang_reflect_Type not implemented'); },
  java_lang_Class_getName0_Ljava_lang_String() { throw ('java_lang_Class_getName0_Ljava_lang_String not implemented'); },
  java_lang_Class_getSuperclass_Ljava_lang_Class() { throw ('java_lang_Class_getSuperclass_Ljava_lang_Class not implemented'); },
  java_lang_Class_isArray_Z() { throw ('java_lang_Class_isArray_Z not implemented'); },
  java_lang_Double_doubleToRawLongBits_DJ(arg0) { throw ('java_lang_Double_doubleToRawLongBits_DJ not implemented'); },
  java_lang_Double_longBitsToDouble_JD(arg0) { throw ('java_lang_Double_longBitsToDouble_JD not implemented'); },
  java_lang_Float_floatToRawIntBits_FI(arg0) { throw ('java_lang_Float_floatToRawIntBits_FI not implemented'); },
  java_lang_Float_intBitsToFloat_IF(arg0) { throw ('java_lang_Float_intBitsToFloat_IF not implemented'); },
  java_lang_Float_parseFloat_Ljava_lang_StringF(arg0) { throw ('java_lang_Float_parseFloat_Ljava_lang_StringF not implemented'); },
  java_lang_Object_getClass_Ljava_lang_Class() { throw ('java_lang_Object_getClass_Ljava_lang_Class not implemented'); },
  java_lang_Object_hashCode_I() { throw ('java_lang_Object_hashCode_I not implemented'); },
  java_lang_Runtime_availableProcessors_I() { throw ('java_lang_Runtime_availableProcessors_I not implemented'); },
  java_lang_Thread_interrupt0_V() { throw ('java_lang_Thread_interrupt0_V not implemented'); },
  java_lang_Thread_isAlive_Z() { throw ('java_lang_Thread_isAlive_Z not implemented'); },
  java_lang_Thread_setNativeName_Ljava_lang_StringV(arg0) { throw ('java_lang_Thread_setNativeName_Ljava_lang_StringV not implemented'); },
  java_lang_Throwable_getStackTraceDepth_I() { throw ('java_lang_Throwable_getStackTraceDepth_I not implemented'); },
  java_lang_Throwable_getStackTraceElement_ILjava_lang_StackTraceElement(arg0) { throw ('java_lang_Throwable_getStackTraceElement_ILjava_lang_StackTraceElement not implemented'); },
  java_nio_ByteBuffer_put_BLjava_nio_ByteBuffer(arg0) { throw ('java_nio_ByteBuffer_put_BLjava_nio_ByteBuffer not implemented'); },
  java_text_DecimalFormatSymbols_getInstance_Ljava_util_LocaleLjava_text_DecimalFormatSymbols(arg0) { throw ('java_text_DecimalFormatSymbols_getInstance_Ljava_util_LocaleLjava_text_DecimalFormatSymbols not implemented'); },
  java_util_AbstractList_get_ILjava_lang_Object(arg0) { throw ('java_util_AbstractList_get_ILjava_lang_Object not implemented'); },
  java_util_AbstractMap_entrySet_Ljava_util_Set() { throw ('java_util_AbstractMap_entrySet_Ljava_util_Set not implemented'); },
  java_util_Formatter_parse_Ljava_lang_StringALjava_util_FormatterXFormatString(arg0) { throw ('java_util_Formatter_parse_Ljava_lang_StringALjava_util_FormatterXFormatString not implemented'); },
  java_util_Locale_initDefault_Ljava_util_LocaleXCategoryLjava_util_Locale(arg0) { throw ('java_util_Locale_initDefault_Ljava_util_LocaleXCategoryLjava_util_Locale not implemented'); },
  java_util_Tripwire_trip_Ljava_lang_ClassLjava_lang_StringV(arg0,arg1) { throw ('java_util_Tripwire_trip_Ljava_lang_ClassLjava_lang_StringV not implemented'); },
  kotlin_collections_IntIterator_nextInt_I() { throw ('kotlin_collections_IntIterator_nextInt_I not implemented'); },
  kotlin_coroutines_jvm_internal_BaseContinuationImpl_invokeSuspend_Ljava_lang_ObjectLjava_lang_Object(arg0) { throw ('kotlin_coroutines_jvm_internal_BaseContinuationImpl_invokeSuspend_Ljava_lang_ObjectLjava_lang_Object not implemented'); },
  kotlin_random_Random_nextBits_II(arg0) { throw ('kotlin_random_Random_nextBits_II not implemented'); },
  me_anno_ecs_prefab_PrefabSaveable_clone_Lme_anno_ecs_prefab_PrefabSaveable() { throw ('me_anno_ecs_prefab_PrefabSaveable_clone_Lme_anno_ecs_prefab_PrefabSaveable not implemented'); },
  me_anno_extensions_ExtensionManager_onDisable_Ljava_util_ListV(arg0) { throw ('me_anno_extensions_ExtensionManager_onDisable_Ljava_util_ListV not implemented'); },
  me_anno_gpu_buffer_OpenGLBuffer_createNioBuffer_V() { throw ('me_anno_gpu_buffer_OpenGLBuffer_createNioBuffer_V not implemented'); },
  me_anno_gpu_shader_OpenGLShader_compile_V() { throw ('me_anno_gpu_shader_OpenGLShader_compile_V not implemented'); },
  me_anno_image_Image_getRGB_II(arg0) { throw ('me_anno_image_Image_getRGB_II not implemented'); },
  me_anno_image_Image_write_Ljava_io_OutputStreamLjava_lang_StringV(arg0,arg1) { throw ('me_anno_image_Image_write_Ljava_io_OutputStreamLjava_lang_StringV not implemented'); },
  me_anno_io_files_FileReference_getChild_Ljava_lang_StringLme_anno_io_files_FileReference(arg0) { throw ('me_anno_io_files_FileReference_getChild_Ljava_lang_StringLme_anno_io_files_FileReference not implemented'); },
  me_anno_io_files_FileReference_getParent_Lme_anno_io_files_FileReference() { throw ('me_anno_io_files_FileReference_getParent_Lme_anno_io_files_FileReference not implemented'); },
  me_anno_io_files_FileReference_inputStream_JLkotlin_jvm_functions_Function2V(arg0,arg1) { throw ('me_anno_io_files_FileReference_inputStream_JLkotlin_jvm_functions_Function2V not implemented'); },
  me_anno_io_files_FileReference_isDirectory_Z() { throw ('me_anno_io_files_FileReference_isDirectory_Z not implemented'); },
  me_anno_io_files_FileReference_length_J() { throw ('me_anno_io_files_FileReference_length_J not implemented'); },
  me_anno_io_zip_InnerFile_getInputStream_Lkotlin_jvm_functions_Function2V(arg0) { throw ('me_anno_io_zip_InnerFile_getInputStream_Lkotlin_jvm_functions_Function2V not implemented'); },
  org_lwjgl_opengl_GL11C_nglGetFloatv_IJV(arg0,arg1) { throw ('org_lwjgl_opengl_GL11C_nglGetFloatv_IJV not implemented'); },
  org_lwjgl_opengl_GL11C_nglTexImage2D_IIIIIIIIJV(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8) { throw ('org_lwjgl_opengl_GL11C_nglTexImage2D_IIIIIIIIJV not implemented'); },
  org_lwjgl_opengl_GL11C_nglTexSubImage2D_IIIIIIIIJV(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8) { throw ('org_lwjgl_opengl_GL11C_nglTexSubImage2D_IIIIIIIIJV not implemented'); },
  org_lwjgl_opengl_GL15C_nglDeleteBuffers_IJV(arg0,arg1) { throw ('org_lwjgl_opengl_GL15C_nglDeleteBuffers_IJV not implemented'); },
  org_lwjgl_opengl_GL30C_glBindRenderbuffer_IIV(arg0,arg1) { throw ('org_lwjgl_opengl_GL30C_glBindRenderbuffer_IIV not implemented'); },
  org_lwjgl_opengl_GL30C_glBlitFramebuffer_IIIIIIIIIIV(arg0,arg1,arg2,arg3,arg4,arg5,arg6,arg7,arg8,arg9) { throw ('org_lwjgl_opengl_GL30C_glBlitFramebuffer_IIIIIIIIIIV not implemented'); },
  org_lwjgl_opengl_GL30C_glFramebufferRenderbuffer_IIIIV(arg0,arg1,arg2,arg3) { throw ('org_lwjgl_opengl_GL30C_glFramebufferRenderbuffer_IIIIV not implemented'); },
  org_lwjgl_opengl_GL30C_glRenderbufferStorageMultisample_IIIIIV(arg0,arg1,arg2,arg3,arg4) { throw ('org_lwjgl_opengl_GL30C_glRenderbufferStorageMultisample_IIIIIV not implemented'); },
  org_lwjgl_opengl_GL30C_glRenderbufferStorage_IIIIV(arg0,arg1,arg2,arg3) { throw ('org_lwjgl_opengl_GL30C_glRenderbufferStorage_IIIIV not implemented'); },
  org_lwjgl_opengl_GL30C_nglDeleteRenderbuffers_IJV(arg0,arg1) { throw ('org_lwjgl_opengl_GL30C_nglDeleteRenderbuffers_IJV not implemented'); },
  org_lwjgl_opengl_GL30C_nglDeleteVertexArrays_IJV(arg0,arg1) { throw ('org_lwjgl_opengl_GL30C_nglDeleteVertexArrays_IJV not implemented'); },
  org_lwjgl_opengl_GL30C_nglGenRenderbuffers_IJV(arg0,arg1) { throw ('org_lwjgl_opengl_GL30C_nglGenRenderbuffers_IJV not implemented'); },
  org_lwjgl_opengl_GL30C_nglGenVertexArrays_IJV(arg0,arg1) { throw ('org_lwjgl_opengl_GL30C_nglGenVertexArrays_IJV not implemented'); },
  org_lwjgl_opengl_GL30C_nglVertexAttribIPointer_IIIIJV(arg0,arg1,arg2,arg3,arg4) { throw ('org_lwjgl_opengl_GL30C_nglVertexAttribIPointer_IIIIJV not implemented'); },
  org_lwjgl_opengl_GL33C_glDrawArraysInstanced_IIIIV(arg0,arg1,arg2,arg3) { throw ('org_lwjgl_opengl_GL33C_glDrawArraysInstanced_IIIIV not implemented'); },
  org_lwjgl_opengl_GL42C_glBindImageTexture_IIIZIIIV(arg0,arg1,arg2,arg3,arg4,arg5,arg6) { throw ('org_lwjgl_opengl_GL42C_glBindImageTexture_IIIZIIIV not implemented'); },
  org_lwjgl_opengl_GL43C_glDispatchCompute_IIIV(arg0,arg1,arg2) { throw ('org_lwjgl_opengl_GL43C_glDispatchCompute_IIIV not implemented'); },
  org_lwjgl_opengl_GL43C_nglDebugMessageCallback_JJV(arg0,arg1) { throw ('org_lwjgl_opengl_GL43C_nglDebugMessageCallback_JJV not implemented'); },
  org_lwjgl_opengl_GL45C_glClipControl_IIV(arg0,arg1) { throw ('org_lwjgl_opengl_GL45C_glClipControl_IIV not implemented'); },
  org_lwjgl_opengl_GL45C_glMemoryBarrier_IV(arg0) { throw ('org_lwjgl_opengl_GL45C_glMemoryBarrier_IV not implemented'); },
  org_lwjgl_opengl_GL45C_glTexImage2DMultisample_IIIIIZV(arg0,arg1,arg2,arg3,arg4,arg5) { throw ('org_lwjgl_opengl_GL45C_glTexImage2DMultisample_IIIIIZV not implemented'); },
  static_java_lang_reflect_AccessibleObject_V() { throw ('static_java_lang_reflect_AccessibleObject_V not implemented'); },
  static_java_util_Formatter_V() { throw ('static_java_util_Formatter_V not implemented'); },
  
  // Initially required memory in 64 kiB pages:
  initialMemorySize: 27
}
export { lib as "autoJS" }