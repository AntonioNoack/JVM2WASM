package engine;

import annotations.*;
import jvm.FillBuffer;
import jvm.GCTraversal;
import jvm.JavaLang;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import me.anno.Time;
import me.anno.cache.AsyncCacheData;
import me.anno.config.DefaultConfig;
import me.anno.ecs.components.mesh.Mesh;
import me.anno.ecs.components.mesh.shapes.IcosahedronModel;
import me.anno.engine.EngineBase;
import me.anno.engine.WindowRenderFlags;
import me.anno.engine.ui.render.RenderMode;
import me.anno.engine.ui.render.SceneView;
import me.anno.fonts.Font;
import me.anno.fonts.FontManager;
import me.anno.fonts.FontStats;
import me.anno.gpu.GFX;
import me.anno.gpu.OSWindow;
import me.anno.gpu.WindowManagement;
import me.anno.gpu.texture.ITexture2D;
import me.anno.gpu.texture.Texture2D;
import me.anno.gpu.texture.TextureCache;
import me.anno.graph.visual.render.RenderGraph;
import me.anno.image.Image;
import me.anno.input.Clipboard;
import me.anno.input.Input;
import me.anno.input.Key;
import me.anno.io.files.FileReference;
import me.anno.io.files.InvalidRef;
import me.anno.io.files.Reference;
import me.anno.io.files.inner.temporary.InnerTmpFile;
import me.anno.io.utils.StringMap;
import me.anno.ui.Panel;
import me.anno.ui.WindowStack;
import me.anno.ui.debug.TestEngine;
import me.anno.utils.Clock;
import me.anno.utils.OS;
import me.anno.utils.async.Callback;
import org.apache.logging.log4j.LoggerImpl;
import org.lwjgl.opengl.GL11C;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static engine.GFXBase2Kt.renderFrame2;
import static jvm.ArrayAccessSafe.arrayLength;
import static jvm.JVM32.*;
import static jvm.JVMShared.getTypeShift;
import static jvm.JVMShared.numClasses;
import static jvm.JavaLang.Object_toString;
import static jvm.LWJGLxGLFW.disableCursor;
import static jvm.NativeLog.log;
import static jvm.ThrowJS.throwJs;

@SuppressWarnings("unused")
public class Engine {

    // todo we could order the fields within Matrix types automatically, so we could upload them without any intermediate copying steps :3

    private static OSWindow window;

    private static void initBrowserFonts() {
        // todo setup everything that JVMPlugin would do...
        FontStats.INSTANCE.setQueryInstalledFontsImpl(Collections::emptyList);
        FontStats.INSTANCE.setGetTextGeneratorImpl(font -> new TextGeneratorImpl(new Font(
                font.getName(),
                FontManager.INSTANCE.getAvgFontSize(font.getSizeIndex()),
                font.getBold(),
                font.getItalic())));
        FontStats.INSTANCE.setGetTextLengthImpl((font, text) -> (double) TextGen.measureText1(font.getName(), font.getSize(), text));
    }

    @NoThrow
    @JavaScript(code = "return 1;")
    public static native boolean runsInBrowser();

    @Export
    @NoThrow
    @SuppressWarnings("ConfusingMainMethod")
    public static void main(String clazzName) {

        if (!runsInBrowser()) {
            OS.isWeb = false;
            OS.isLinux = true;
        }

        // Build.setShipped(true);

        // LuaTest.test();
        // SciMark.test();

        if (runsInBrowser()) {
            initBrowserFonts();
        }

        EngineBase instance;
        //instance = (StudioBase) JavaLang.Class_forName(clazzName).newInstance();
        Panel panel;
        // panel = new Snake();
        // panel = new CodeEditor(DefaultConfig.INSTANCE.getStyle());
        // panel = new AnimTextPanelTest(false);
        // panel = CellMod.createGame();

        Mesh icoSphere = IcosahedronModel.INSTANCE.createIcosphere(1, 1f, new Mesh());

        log("Created IcoSphere");

        panel = SceneView.Companion.testScene(icoSphere, sceneView -> {
            sceneView.getRenderView().setRenderMode(RenderMode.Companion.getDEFAULT());
            return Unit.INSTANCE;
        });

        panel.setWeight(1f);
        instance = new TestEngine("Engine", () -> Collections.singletonList(panel));
        instance.run(false);

        Clock tick = new Clock("Engine");

        window = new WebGLWindow(me.anno.Engine.getProjectName());
        WindowManagement.createWindow(window, tick);
        WindowManagement.prepareForRendering(tick);

        GFX.setupBasics(tick);

        tick.stop("GFX.setupBasics");

        // todo does WebGL ever support depth textures?
        GFX.supportsDepthTextures = !runsInBrowser();
        RenderGraph.INSTANCE.setThrowExceptions(true);

        GFX.check("main");

        instance.gameInit();
        WindowRenderFlags.INSTANCE.setShowFPS(true);
        DefaultConfig.INSTANCE.set("debug.ui.showRenderTimes", true);
        DefaultConfig.INSTANCE.set("debug.ui.showDebugFrames", true);

        tick.stop("Game Init");
    }

    @Export
    @NoThrow
    public static void update(int width, int height, float dt) {
        window.setWidth(width);
        window.setHeight(height);
        // todo vsync is enabled when calling this (good), but when I "toggle Vsync" via the menu, it doesn't get toggled
        // window.updateVsync();
        if (runsInBrowser()) {
            window.setFramesSinceLastInteraction(0);// redraw is required to prevent flickering
        }
        WindowManagement.updateWindows();
        Time.updateTime(dt, System.nanoTime());
        renderFrame2(window); // easier, less stuff from other systems
    }

    @Export
    public static void mouseMove(float mouseX, float mouseY) {
        if (window == null) return;
        Input.INSTANCE.onMouseMove(window, mouseX, mouseY);
    }

    @Export
    public static void keyDown(int key) {
        if (window == null) return;
        Input.INSTANCE.onKeyPressed(window, Key.Companion.byId(key), System.nanoTime());
    }

    @Export
    public static void keyUp(int key) {
        if (window == null) return;
        Input.INSTANCE.onKeyReleased(window, Key.Companion.byId(key));
    }

    @Export
    public static void keyTyped(int key) {
        if (window == null) return;
        Input.INSTANCE.onKeyTyped(window, Key.Companion.byId(key));
    }

    @Export
    public static void charTyped(int key, int mods) {
        if (window == null) return;
        Input.INSTANCE.onCharTyped(window, key, mods);
    }

    @Export
    public static void mouseDown(int key) {
        if (window == null) return;
        Input.INSTANCE.onMousePress(window, Key.Companion.byId(key));
    }

    @Export
    public static void mouseUp(int key) {
        if (window == null) return;
        Input.INSTANCE.onMouseRelease(window, Key.Companion.byId(key));
    }

    @Export
    public static void mouseWheel(float dx, float dy) {
        if (window == null) return;
        Input.INSTANCE.onMouseWheel(window, dx, dy, true);
    }

    @Export
    public static void keyModState(int state) {
        // control: 2
        // shift: 1
        // capslock: 16
        // alt: 4
        // super: 8
        Input.INSTANCE.setKeyModState(state);
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_GFX_check_V")
    @WASM(code = "")
    public static native void me_anno_gpu_GFX_check_V();

    @NoThrow
    @JavaScript(code = "let loc = window.location.href;\n" +
            "let dir = loc.substring(0, loc.lastIndexOf('/'));" +
            "return fill(arg0,dir+'/assets/')")
    private static native int fillURL(char[] chars);

    private static String baseURL;

    private static String getBaseURL() {
        if (baseURL == null) {
            char[] buffer = FillBuffer.getBuffer();
            int length = fillURL(buffer);
            baseURL = new String(buffer, 0, length);
        }
        return baseURL;
    }

    @Alias(names = "me_anno_io_utils_StringMap_saveMaybe_Ljava_lang_StringV")
    private static void saveMaybe(StringMap self, String name) { // engine gets stuck after calling this :/
        // todo make this work???
    }

    @Alias(names = "me_anno_io_files_Reference_createReference_Ljava_lang_StringLme_anno_io_files_FileReference")
    public static FileReference Reference_createReference(String str) {
        String str2 = str.indexOf('\\') >= 0 ? str.replace('\\', '/') : str;

        if (str.startsWith("https://") || str.startsWith("http://")) {
            return new WebRef2(str2);
        }

        if (str.startsWith("res://")) {
            String url = getBaseURL() + str2.substring(6);
            return new WebRef2(url);
        }

        if (str.startsWith("tmp://")) {
            FileReference tmpRef = InnerTmpFile.find(str);
            if (tmpRef == null) {
                log("Missing temporary file {}, probably GCed", str);
                return InvalidRef.INSTANCE;
            }
            return tmpRef;
        }

        FileReference staticRef = Reference.queryStatic(str);
        if (staticRef != null) {
            return staticRef;
        }

        while (str.endsWith("/")) str = str.substring(0, str.length() - 1);
        return new VirtualFileRef(str);
    }

    @Alias(names = "me_anno_io_files_Reference_getReference_Ljava_lang_StringLme_anno_io_files_FileReference")
    public static FileReference Reference_getReference(String str) {
        return Reference_createReference(str);
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_GFXBase_setIcon_JV")
    public static void me_anno_gpu_GFXBase_setIcon_JV(long window) {
    }

    @JavaScript(code = "" +
            "let img = new Image();\n" +
            "gcLock(arg1);\n" +
            "gcLock(arg2);\n" +
            "img.onload=function(){\n" +
            "   let w=img.width,h=img.height;\n" +
            "   let canvas=document.createElement('canvas')\n" +
            "   canvas.width=w;canvas.height=h;\n" +
            "   let ctx=canvas.getContext('2d')\n" +
            "   ctx.drawImage(img,0,0,w,h);\n" +
            "   let x=window.lib.prepareTexture(arg1);\n" +
            "   if(x) throw x;\n" +
            "   gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,ctx.getImageData(0,0,w,h).data);\n" +
            "   x=window.lib.finishTexture(arg1,w,h,arg2);\n" +
            "   if(x) throw x;\n" +
            "   gcUnlock(arg1);\n" +
            "   gcUnlock(arg2);\n" +
            "}\n" +
            "lib.onerror=function(){\n" +
            "   let x=window.lib.finishTexture(0,-1,-1,arg2);\n" +
            "   if(x) throw x;\n" +
            "   gcUnlock(arg1);\n" +
            "   gcUnlock(arg2);\n" +
            "}\n" +
            "img.src = str(arg0);\n" +
            "")
    private static native void generateTexture(String path, Texture2D texture, Callback<ITexture2D> callback);

    @Export
    @UsedIfIndexed
    @Alias(names = "prepareTexture")
    public static void prepareTexture(Texture2D texture) {
        texture.ensurePointer();
        Texture2D.Companion.bindTexture(texture.getTarget(), texture.getPointer());
    }

    @Export
    @UsedIfIndexed
    @Alias(names = "finishTexture")
    public static void finishTexture(Texture2D texture, int w, int h, Callback<ITexture2D> callback) {
        if (texture != null) {
            texture.setWidth(w);
            texture.setHeight(h);
            texture.setCreatedW(w);
            texture.setCreatedH(h);
            texture.setInternalFormat(GL11C.GL_RGB8);
            texture.afterUpload(false, 4, 4);
        }
        if (texture != null) texture.checkSession();
        log("Finishing texture", String.valueOf(texture), String.valueOf(texture != null && texture.isCreated()));
        if (callback != null) {
            callback.ok(texture);
        }
    }

    @Alias(names = "me_anno_image_ImageGPUCache_get_Lme_anno_io_files_FileReferenceJZLme_anno_gpu_texture_Texture2D")
    public static Texture2D ImageGPUCache_get(Object self, FileReference file, long timeout, boolean async) {
        if (!async) throw new IllegalArgumentException("Non-async textures are not supported in Web");
        // log("asking for", file.getAbsolutePath());
        AsyncCacheData<ITexture2D> tex = TextureCache.INSTANCE.getLateinitTexture(file, timeout, false, (callback) -> {
            if (file instanceof WebRef2) {
                // call JS to generate a texture for us :)
                Texture2D tex3 = new Texture2D(file.getName(), 1, 1, 1);
                generateTexture(file.getAbsolutePath(), tex3, callback);
            } else {
                log("Reading local images hasn't been implemented yet", file.getAbsolutePath());
                callback.err(new IOException("Reading local images hasn't been implemented yet"));
            }
            return Unit.INSTANCE;
        });
        if (tex == null) return null;
        ITexture2D tex2 = tex.getValue();
        if (!(tex2 instanceof Texture2D)) return null;
        return (Texture2D) tex2;
    }

    @Alias(names = "me_anno_io_files_thumbs_Thumbs_generateSystemIcon_Lme_anno_io_files_FileReferenceLme_anno_io_files_FileReferenceILkotlin_jvm_functions_Function2V")
    public static void Thumbs_generateSystemIcon(FileReference src, FileReference dst, int size, Function2<ITexture2D, Exception, Unit> callback) {
        // saves references to swing
        callback.invoke(null, exc);
    }

    @Alias(names = "me_anno_utils_files_FileExplorerSelectWrapper_selectFileOrFolder_Ljava_io_FileZLkotlin_jvm_functions_Function1V")
    public static void FileExplorerSelectWrapper_selectFileOrFolder(File file, boolean folder, Function1<File, Unit> callback) {
        // saves swing.JFileChooser
        // todo select file by the user
        log("Selecting files is not yet implemented!");
        callback.invoke(null);
    }

    // todo remove all methods related to Regex, and see how much the effect is
    /*@Alias(name = "java_util_regex_Pattern_compile_Ljava_lang_StringLjava_util_regex_Pattern")
    public static Pattern java_util_regex_Pattern_compile_Ljava_lang_StringLjava_util_regex_Pattern(String str) {
        throw new NotImplementedError("Regex was removed from Rem's Engine/Web, because it's huge");
    }

    @Alias(name = "static_java_util_Formatter_V")
    public static void static_java_util_Formatter_V(){}

    @Alias(name = "java_lang_String_replaceFirst_Ljava_lang_StringLjava_lang_StringLjava_lang_String")
    public static void String_replaceFirst() {
        throw new NotImplementedError("Regex was removed from Rem's Engine/Web, because it's huge");
    }*/

    private static final RuntimeException exc = new RuntimeException("Thumbs.generateSystemIcon is not supported");

    @NoThrow
    @Alias(names = "me_anno_engine_ui_render_MovingGrid_drawTextMesh_DIV")
    public static void me_anno_engine_ui_render_MovingGrid_drawTextMesh_DIV(Object self, double baseSize, int factor) {
        // todo support text meshes, and implement this :)
    }

    @Alias(names = "me_anno_audio_openal_AudioManager_checkIsDestroyed_V")
    public static void me_anno_audio_openal_AudioManager_checkIsDestroyed_V() {
        // cannot be destroyed, as far as I know :)
    }

    @Alias(names = "me_anno_gpu_OSWindow_addCallbacks_V")
    public static void me_anno_gpu_OSWindow_addCallbacks_V(OSWindow self) {
        // not needed
    }

    @Alias(names = "me_anno_image_exr_EXRReader_read_Ljava_io_InputStreamLme_anno_image_Image")
    public static Object me_anno_image_exr_EXRReader_read_Ljava_io_InputStreamLme_anno_image_Image(InputStream stream) throws IOException {
        log("EXR is not supported!");
        stream.close();
        return null;
    }

    @Alias(names = "me_anno_image_exr_EXRReader_read_Ljava_nio_ByteBufferLme_anno_image_Image")
    public static Object me_anno_image_exr_EXRReader_read_Ljava_nio_ByteBufferLme_anno_image_Image(ByteBuffer buffer) {
        log("EXR is not supported!");
        return null;
    }

    // could be disabled, if it really is needed...
	/*@Alias(names = "me_anno_image_gimp_GimpImageXCompanion_readAsFolder_Lme_anno_io_files_FileReferenceLkotlin_jvm_functions_Function2V")
	public static void GimpImageXCompanion_readAsFolder(FileReference src, Function2<GimpImage, Exception, Unit> callback) {
		callback.invoke(null, new IOException("Gimp Image files are not supported in Web!"));
	}*/

    @Alias(names = "me_anno_image_gimp_GimpImageXCompanion_read_Ljava_io_InputStreamLme_anno_image_Image")
    public static Object me_anno_image_gimp_GimpImageXCompanion_read_Ljava_io_InputStreamLme_anno_image_Image(InputStream stream) throws IOException {
        stream.close();
        throw new IOException("Gimp Image files are not supported in Web!");
    }

    @Alias(names = "me_anno_gpu_texture_Texture2D_create_Ljava_awt_image_BufferedImageZZV")
    private static void Texture2D_create_Ljava_awt_image_BufferedImageZZV(Texture2D self, Object img, boolean a, boolean b) {
        throw new IllegalArgumentException("Cannot create texture from BufferedImage in Web");
    }

    // for removing a lot of dependencies, e.g., to InnerFolderCache -> SVHMesh -> Prefab
    @Alias(names = "me_anno_io_files_FileReference_isSerializedFolder_Z")
    private static boolean FileReference_isSerializedFolder(FileReference self) {
        return false;
    }

    // for removing a lot of dependencies, e.g., to InnerFolderCache -> SVHMesh -> Prefab
    @Alias(names = "me_anno_io_zip_InnerFile_getChild_Ljava_lang_StringLme_anno_io_files_FileReference")
    private static FileReference InnerFile_getChild(FileReference self, String name) {
        return InvalidRef.INSTANCE;
    }

    // for removing a lot of dependencies, e.g., to InnerFolderCache -> SVHMesh -> Prefab
    @Alias(names = "me_anno_io_zip_InnerFile_getZipFileForDirectory_Lme_anno_io_files_FileReference")
    private static FileReference InnerFile_getZipFileForDirectory(FileReference self) {
        return InvalidRef.INSTANCE;
    }

    // only needs to be re-enabled, when we have controller support; until then, we can save space
    @NoThrow
    @Alias(names = "me_anno_input_Input_pollControllers_Lme_anno_gpu_OSWindowV")
    public static void me_anno_input_Input_pollControllers_Lme_anno_gpu_OSWindowV(Object self, Object window) {
    }

    // to save space for now
    @NoThrow
    @Alias(names = "me_anno_io_config_ConfigBasics_loadConfig_Ljava_lang_StringLme_anno_io_files_FileReferenceLme_anno_io_utils_StringMapZLme_anno_io_utils_StringMap")
    private static StringMap ConfigBasics_loadConfig(String name, FileReference workspace, StringMap defaultValue, boolean saveIfMissing) {
        return defaultValue;
    }

    // trying to save space by getting rid of TextWriter (1 MB in wasm text)
    @Alias(names = "me_anno_io_Saveable_toString_Ljava_lang_String")
    private static String Saveable_toString_Ljava_lang_String(Object self) {
        return Object_toString(self);
    }

    // trying to save space by getting rid of TextWriter (1 MB in wasm text)
    @Alias(names = "me_anno_input_Input_copy_Lme_anno_gpu_OSWindowV")
    private static void Input_copy(Object self, OSWindow window) {
        float mouseX = window.getMouseX();
        float mouseY = window.getMouseY();
        WindowStack dws = window.getWindowStack();
        List<Panel> inFocus = dws.getInFocus();
        Panel inFocus0 = dws.getInFocus0();
        if (inFocus0 == null) return;
        if (inFocus.isEmpty()) return;
        Object copied = inFocus0.onCopyRequested(mouseX, mouseY);
        if (copied == null) return;
        Clipboard.INSTANCE.setClipboardContent(copied.toString());
    }

    @Alias(names = "kotlin_text_CharsKt__CharJVMKt_checkRadix_II")
    private static int checkRadix(int radix) {
        if (radix < 2 || radix > 36)
            throw new IllegalArgumentException("Illegal Radix");
        return radix;
    }

    // removing spellchecking for now (600 kiB wasm text)
    @Alias(names = "me_anno_language_spellcheck_Spellchecking_check_Ljava_lang_CharSequenceZZLjava_util_List")
    public static Object me_anno_language_spellcheck_Spellchecking_check_Ljava_lang_CharSequenceZZLjava_util_List(
            CharSequence t, boolean allowFirstLowerCase, boolean async) {
        return null;
    }

    @Alias(names = "me_anno_ui_input_components_CorrectingTextInput_getSuggestions_Ljava_util_List")
    public static Object me_anno_ui_input_components_CorrectingTextInput_getSuggestions_Ljava_util_List(Object self) {
        return null;
    }

    @NoThrow
    @JavaScript(code = "try { return BigInt(window.performance.memory.usedJSHeapSize); } catch(e){ return 0n; }")
    @Alias(names = "me_anno_ui_debug_JSMemory_jsUsedMemory_J")
    public static native long me_anno_ui_debug_JSMemory_jsUsedMemory_J();

    @NoThrow
    @Alias(names = "me_anno_extensions_ExtensionLoader_loadInfoFromZip_Lme_anno_io_files_FileReferenceLme_anno_extensions_ExtensionInfo")
    public static Object me_anno_extensions_ExtensionLoader_loadInfoFromZip_Lme_anno_io_files_FileReferenceLme_anno_extensions_ExtensionInfo(Object file) {
        // there is no Zip, extensions should be loaded directly ^^
        return null;
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_LogoKt_drawLogo_IIZZ")
    public static boolean me_anno_gpu_LogoKt_drawLogo(int a, int b, boolean c) {
        return true;
    }

    @NoThrow
    @Alias(names = "me_anno_image_ImageCPUCache_get_Lme_anno_io_files_FileReferenceJZLme_anno_image_Image")
    public static Image me_anno_image_ImageCPUCache_get_Lme_anno_io_files_FileReferenceJZLme_anno_image_Image(FileReference path, long timeout, boolean async) {
        // todo create image async using JavaScript
        log("Todo: create image async using JS", path.getAbsolutePath());
        return null;
    }

    @NoThrow
    @Alias(names = "me_anno_image_ImageCPUCache_get_Lme_anno_io_files_FileReferenceZLme_anno_image_Image")
    public static Image me_anno_image_ImageCPUCache_get_Lme_anno_io_files_FileReferenceZLme_anno_image_Image(FileReference path, boolean async) {
        return me_anno_image_ImageCPUCache_get_Lme_anno_io_files_FileReferenceJZLme_anno_image_Image(path, 10_000, async);
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_GFXBase_addCallbacks_Lme_anno_gpu_OSWindowV")
    public static void me_anno_gpu_GFXBase_addCallbacks_Lme_anno_gpu_OSWindowV(Object window) {
        // we'll call it directly, no need for callbacks
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_GFXBase_close_Lme_anno_gpu_OSWindowV")
    public static void me_anno_gpu_GFXBase_close_Lme_anno_gpu_OSWindowV(Object window) {
        // not really supported
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_GFXBase_loadRenderDoc_V")
    public static void me_anno_gpu_GFXBase_loadRenderDoc_V() {
        // not supported
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_OSWindow_forceUpdateVsync_V")
    public static void me_anno_gpu_OSWindow_forceUpdateVsync_V(Object self) {
        // not supported
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_OSWindow_toggleFullscreen_V")
    public static void me_anno_gpu_OSWindow_toggleFullscreen_V(Object self) {
        disableCursor();
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_OSWindow_updateMousePosition_V")
    public static void me_anno_gpu_OSWindow_updateMousePosition_V(Object self) {
        // done automatically
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_GFXBase_handleClose_Lme_anno_gpu_OSWindowV")
    public static void me_anno_gpu_GFXBase_handleClose_Lme_anno_gpu_OSWindowV(Object window) {
        // idc really, should not be callable
    }

    @NoThrow
    @Alias(names = "me_anno_input_Input_setClipboardContent_Ljava_lang_StringV")
    @JavaScript(code = "if(arg1) navigator.clipboard.writeText(str(arg1))")
    public static native void me_anno_input_Input_setClipboardContent_Ljava_lang_StringV(Object self, String txt);

    @NoThrow
    @Alias(names = "me_anno_gpu_monitor_SubpixelLayout_detect_V")
    public static void me_anno_gpu_monitor_SubpixelLayout_detect_V(Object self) {
        // todo implement this
        log("Todo: detect subpixel layout");
    }

    @NoThrow
    @Alias(names = "me_anno_extensions_ExtensionLoader_load_V")
    public static void me_anno_extensions_ExtensionLoader_load_V() {
        // just skip for now 😄, 8k lines (out of 236k)
    }

    @Alias(names = "me_anno_utils_process_BetterProcessBuilder_start_Ljava_lang_Process")
    public static Process me_anno_utils_process_BetterProcessBuilder_start_Ljava_lang_Process(Object self) {
        throw new RuntimeException("Starting processes is not possible in WASM");
    }

    @Alias(names = "me_anno_utils_Sleep_waitForGFXThreadUntilDefined_ZLkotlin_jvm_functions_Function0_Ljava_lang_Object")
    public static Object waitUntilDefined(boolean killable, Object func) {
        throwJs("Cannot wait in Browser");
        return null;
    }

    /**
     * for debugging, prints all strings that were generated at runtime
     */
    @NoThrow // todo what are idx0 and len???
    private static void printDynamicStrings(int idx0, int len) {
        int instance = getAllocationStart();
        final int endPtr = getNextPtr();
        int idx = 0;
        final int nc = numClasses();
        int endIdx = idx0 + len;
        int lastClass = -1;
        int lastSize = 0;
        int instanceCtr = 0;
        while (unsignedLessThan(instance, endPtr) && idx < endIdx) {

            // when we find a not-used section, replace it with byte[] for faster future traversal (if possible)
            final int clazz = readClassId(instance);
            if (unsignedGreaterThanEqual(clazz, nc)) {
                log("Handling", instance, clazz);
                log("Illegal class index {} >= {} at {}!", clazz, nc, instance);
                return;
            }

            int size;
            if (unsignedLessThan(clazz - 1, 9)) { // clazz > 0 && clazz < 10
                // handle arrays by size
                size = arrayOverhead + (arrayLength(instance) << getTypeShift(clazz));
            } else {
                // handle class instance
                size = GCTraversal.classSizes[clazz];
            }

            size = adjustCallocSize(size);

            if (clazz == 10) {
                log("found instance", idx, instance);
                if (unsignedLessThan(idx - idx0, len)) {
                    log((String) ptrTo(instance));
                }
                idx++;
            } else {
                if (clazz == lastClass) {
                    lastSize += size;
                    instanceCtr++;
                } else {
                    if (lastClass >= 0) {
                        log("clazz", lastClass, lastSize, instanceCtr);
                    }
                    lastClass = clazz;
                    lastSize = size;
                    instanceCtr = 1;
                }
            }
            instance += size;
        }
        log("clazz (last)", lastClass, lastSize, instanceCtr);
    }

    @NoThrow
    @JavaScript(code = "" +
            "console.log('running thread', str(arg1));\n" +
            "safe(window.lib.runRunnable(arg0));\n")
    private static native void runAsyncImpl(Function0<Object> runnable, String name);

    @Export
    @UsedIfIndexed
    @Alias(names = "runRunnable")
    private static void runRunnable(Function0<Object> runnable) {
        runnable.invoke();
    }

    @NoThrow
    @Alias(names = "me_anno_cache_CacheSection_runAsync_Ljava_lang_StringLkotlin_jvm_functions_Function0V")
    public static void runAsync(Object self, String name, Function0<Object> runnable) {
        runAsyncImpl(runnable, name);
    }

    @Alias(names = "me_anno_engine_OfficialExtensions_register_V")
    private static void me_anno_engine_OfficialExtensions_register_V(Object self) {
    }

    @Alias(names = "org_apache_logging_log4j_LoggerImpl_print_Ljava_lang_StringLjava_lang_StringV")
    public static void LoggerImpl_print(LoggerImpl self, String prefix, String text) {
        // avoid printing line-by-line like the original, because JS saves the stack trace for each warning
        String line2 = "[" + LoggerImpl.Companion.getTimeStamp() + "," + self.getPrefix() + self.getSuffix() + "] " + text;
        LoggerImpl_printRaw(self, prefix, line2);
    }

    @NoThrow
    @Alias(names = "org_apache_logging_log4j_LoggerImpl_printRaw_Ljava_lang_StringLjava_lang_StringV")
    public static void LoggerImpl_printRaw(LoggerImpl self, String prefix, String line) {
        boolean justLog = !(Objects.equals(prefix, "ERR!") || Objects.equals(prefix, "WARN"));
        JavaLang.printString(line, justLog);
    }
}
