package engine;

import annotations.*;
import jvm.FillBuffer;
import jvm.JavaLang;
import jvm.Pointer;
import jvm.custom.ThreadLocalRandom;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import me.anno.Time;
import me.anno.cache.AsyncCacheData;
import me.anno.ecs.Entity;
import me.anno.ecs.components.mesh.Mesh;
import me.anno.ecs.components.mesh.MeshComponent;
import me.anno.ecs.components.mesh.shapes.IcosahedronModel;
import me.anno.engine.EngineBase;
import me.anno.engine.Events;
import me.anno.engine.WindowRenderFlags;
import me.anno.engine.ui.control.DraggingControlSettings;
import me.anno.engine.ui.render.SceneView;
import me.anno.engine.ui.render.SuperMaterial;
import me.anno.fonts.Font;
import me.anno.fonts.FontManager;
import me.anno.fonts.FontStats;
import me.anno.gpu.*;
import me.anno.gpu.texture.ITexture2D;
import me.anno.gpu.texture.Texture2D;
import me.anno.gpu.texture.TextureCache;
import me.anno.graph.visual.render.RenderGraph;
import me.anno.image.Image;
import me.anno.image.ImageCache;
import me.anno.image.raw.GPUImage;
import me.anno.image.raw.IntImage;
import me.anno.input.Clipboard;
import me.anno.input.Input;
import me.anno.input.Key;
import me.anno.io.files.BundledRef;
import me.anno.io.files.FileReference;
import me.anno.io.files.InvalidRef;
import me.anno.io.files.Reference;
import me.anno.io.files.inner.temporary.InnerTmpFile;
import me.anno.io.files.inner.temporary.InnerTmpImageFile;
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
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static jvm.JVMShared.arrayOverhead;
import static jvm.JVMShared.castToPtr;
import static jvm.JavaLang.Object_toString;
import static jvm.LWJGLxGLFW.disableCursor;
import static jvm.NativeLog.log;
import static jvm.Pointer.add;
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
        FontStats.INSTANCE.setGetTextLengthImpl((font, text) ->
                Double.valueOf(TextGen.measureText1(font.getName(), font.getSize(), text)));
    }

    @NoThrow
    @JavaScript(code = "return 1;")
    public static native boolean runsInBrowser();

    private static void markFieldsAsUsed() {
        // todo add an annotation/setting to mark these fields as used
        DraggingControlSettings settings = new DraggingControlSettings();
        settings.setRenderMode(settings.getRenderMode());
        settings.setShowDebugFrames(true);
        settings.setShowRenderTimes(true);
        settings.setSuperMaterial(SuperMaterial.Companion.getNONE());
    }

    @Export
    @NoThrow
    @Alias(names = "EngineMain")
    public static void EngineMain(String clazzName) {

        if (!runsInBrowser()) {
            OS.isWeb = false;
            OS.isLinux = true;
        }

        initImageReader();

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

        Mesh icoSphere = IcosahedronModel.INSTANCE.createIcosphere(3, 1f, new Mesh());

        log("Created IcoSphere");

        Entity scene = new Entity("Scene");
        scene.add(new MeshComponent(icoSphere));
        panel = SceneView.Companion.testScene(scene, null);

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

        markFieldsAsUsed();

        tick.stop("Game Init");
    }

    @Export
    @NoThrow
    public static void update(int width, int height, float dt) {
        window.setWidth(width);
        window.setHeight(height);
        window.updateVsync();
        WindowManagement.updateWindows();
        Time.updateTime(dt, System.nanoTime());

        GFX.activeWindow = window;
        RenderStep.beforeRenderSteps();
        RenderStep.renderStep(window, true);

        if (Input.INSTANCE.isShiftDown()) {
            SpinningCube.renderSpinningCube((float) Time.getGameTime(), (float) width / (float) height);
        }
    }

    private static void addEvent(Runnable runnable) {
        Events.INSTANCE.addEvent(() -> {
            runnable.run();
            return Unit.INSTANCE;
        });
    }

    @Export
    public static void mouseMove(float mouseX, float mouseY) {
        if (window == null) return;
        addEvent(() -> Input.INSTANCE.onMouseMove(window, mouseX, mouseY));
    }

    @Export
    public static void keyDown(int key) {
        if (window == null) return;
        long time = System.nanoTime();
        addEvent(() -> Input.INSTANCE.onKeyPressed(window, Key.Companion.byId(key), time));
    }

    @Export
    public static void keyUp(int key) {
        if (window == null) return;
        addEvent(() -> Input.INSTANCE.onKeyReleased(window, Key.Companion.byId(key)));
    }

    @Export
    public static void keyTyped(int key) {
        if (window == null) return;
        addEvent(() -> Input.INSTANCE.onKeyTyped(window, Key.Companion.byId(key)));
    }

    @Export
    public static void charTyped(int key, int mods) {
        if (window == null) return;
        addEvent(() -> Input.INSTANCE.onCharTyped(window, key, mods));
    }

    @Export
    public static void mouseDown(int key) {
        if (window == null) return;
        addEvent(() -> Input.INSTANCE.onMousePress(window, Key.Companion.byId(key)));
    }

    @Export
    public static void mouseUp(int key) {
        if (window == null) return;
        addEvent(() -> Input.INSTANCE.onMouseRelease(window, Key.Companion.byId(key)));
    }

    @Export
    public static void mouseWheel(float dx, float dy) {
        if (window == null) return;
        addEvent(() -> Input.INSTANCE.onMouseWheel(window, dx, dy, true));
    }

    @Export
    public static void keyModState(int state) {
        // shift: 1, control: 2, alt: 4, super: 8, capslock: 16
        addEvent(() -> Input.INSTANCE.setKeyModState(state));
    }

    @Export
    public static void focusState(boolean inFocus) {
        addEvent(() -> window.setInFocus(inFocus));
    }

    @Export
    public static void minimizedState(boolean isMinimized) {
        addEvent(() -> window.setMinimized(isMinimized));
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_GFX_check_V")
    @WASM(code = "")
    public static native void me_anno_gpu_GFX_check_V();

    @NoThrow
    @JavaScript(code = "let loc = window.location.href;\n" +
            "let dir = loc.substring(0, loc.lastIndexOf('/'));" +
            "return fill(arg0,dir+'/../../assets/')")
    private static native int fillBaseURL(char[] chars);

    private static String baseURL;

    private static String getBaseURL() {
        if (baseURL == null) {
            char[] buffer = FillBuffer.getBuffer();
            int length = fillBaseURL(buffer);
            baseURL = new String(buffer, 0, length);
        }
        return baseURL;
    }

    @Alias(names = "me_anno_io_files_BundledRef_getExists_Z")
    private static boolean BundledRef_getExists_Z(BundledRef self) {
        return true;
    }

    @Alias(names = "me_anno_io_utils_StringMap_saveMaybe_Ljava_lang_StringV")
    private static void saveMaybe(StringMap self, String name) { // engine gets stuck after calling this :/
        // todo make this work???
    }

    @Alias(names = "me_anno_io_files_Reference_createReference_Ljava_lang_StringLme_anno_io_files_FileReference")
    public static FileReference Reference_createReference(String uri) {
        uri = uri.indexOf('\\') >= 0 ? uri.replace('\\', '/') : uri;
        while (uri.endsWith("/")) uri = uri.substring(0, uri.length() - 1);

        if (uri.startsWith("https://") || uri.startsWith("http://")) {
            return new WebRef2(uri);
        }

        String bundledRefPrefix = BundledRef.PREFIX;
        if (uri.startsWith(bundledRefPrefix)) {
            String url = getBaseURL() + uri.substring(bundledRefPrefix.length());
            return new WebRef2(url);
        }

        if (uri.startsWith("tmp://")) {
            FileReference tmpRef = InnerTmpFile.find(uri);
            if (tmpRef == null) {
                log("Missing temporary file {}, probably GCed", uri);
                return InvalidRef.INSTANCE;
            }
            return tmpRef;
        }

        FileReference staticReference = Reference.queryStatic(uri);
        if (staticReference != null) {
            return staticReference;
        }

        return new VirtualFileRef(uri);
    }

    @Alias(names = "me_anno_io_files_Reference_getReference_Ljava_lang_StringLme_anno_io_files_FileReference")
    public static FileReference Reference_getReference(String str) {
        return Reference_createReference(str);
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_GFXBase_setIcon_JV")
    public static void me_anno_gpu_GFXBase_setIcon_JV(long window) {
    }

    private static void initImageReader() {
        ImageCache.INSTANCE.registerByteArrayReader("png,jpg", Engine::loadImageAsync);
    }

    @Alias(names = "loadTextureAsync")
    @JavaScript(code = "" +
            "let img = new Image();\n" +
            "gcLock(arg1);\n" +
            "gcLock(arg2);\n" +
            "img.onload=function(){\n" +
            "   let w=img.width,h=img.height;\n" +
            "   let canvas=document.createElement('canvas')\n" +
            "   canvas.width=w;canvas.height=h;\n" +
            "   let ctx=canvas.getContext('2d');\n" +
            "   ctx.save(); ctx.scale(1,-1);\n" +
            "   ctx.drawImage(img,0,0,w,-h);\n" +
            "   ctx.restore();\n" +
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
    private static native void loadTextureAsync(String path, Texture2D texture, Callback<?> textureCallback);

    @Alias(names = "loadImageAsync")
    @JavaScript(code = "" +
            "console.log('Loading image from bytes...');\n" +
            "let bytes = arg0, numBytes = arg1, callback = arg2;\n" +
            "let img = new Image();\n" +
            "gcLock(callback);\n" + // callback
            "img.onload=function(){\n" +
            "   let w=img.width,h=img.height;\n" +
            "   let canvas=document.createElement('canvas')\n" +
            "   canvas.width=w;canvas.height=h;\n" +
            "   let ctx=canvas.getContext('2d');\n" +
            "   ctx.save(); ctx.scale(1,-1);\n" +
            "   ctx.drawImage(img,0,0,w,-h);\n" +
            "   ctx.restore();\n" +
            //  Uint8ClampedArray representing a one-dimensional array containing the data in the RGBA order
            "   let data = ctx.getImageData(0,0,w,h).data;\n" +
            "   let intArray = lib.createIntArray(w*h);\n" +
            "   memory.buffer.set(data, intArray + arrayOverhead);\n" +
            "   let x=window.lib.finishImage(intArray,w,h,callback);\n" +
            "   if(x) throw x;\n" +
            "   gcUnlock(callback);\n" +
            "}\n" +
            "lib.onerror=function(){\n" +
            "   let x=window.lib.finishImage(0,-1,-1,0,callback);\n" +
            "   if(x) throw x;\n" +
            "   gcUnlock(callback);\n" +
            "}\n" +
            "const uint8Array = new Uint8Array(memory.buffer,bytes,numBytes);\n" +
            "const blob = new Blob([uint8Array]);\n" +
            "img.src = URL.createObjectURL(blob);\n" +
            "")
    private static native void loadImageAsync(Pointer bytes, int numBytes, Callback<?> callback);

    private static void loadImageAsync(FileReference sourceFile, byte[] bytes, Callback<?> callback) {
        Pointer bytesPtr = add(castToPtr(bytes), arrayOverhead);
        loadImageAsync(bytesPtr, bytes.length, callback);
    }

    @Alias(names = "finishImage")
    private static void finishImage(int[] data, int width, int height, boolean hasAlpha, Callback<IntImage> callback) {
        if (data != null) callback.ok(new IntImage(width, height, data, hasAlpha));
        else callback.err(new IOException("Failed reading image"));
    }

    @NoThrow
    @Alias(names = "createIntArray")
    private static int[] createIntArray(int length) {
        return new int[length];
    }

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
        // log("Finishing texture", String.valueOf(texture), String.valueOf(texture != null && texture.isCreated()));
        if (callback != null) {
            callback.ok(texture);
        }
    }

    @Alias(names = "me_anno_gpu_texture_TextureCache_get_Lme_anno_io_files_FileReferenceJZLme_anno_gpu_texture_ITexture2D")
    public static ITexture2D TextureCache_get(Object self, FileReference file, long timeout, boolean async) {
        if (file == InvalidRef.INSTANCE) return null;
        if (file instanceof InnerTmpImageFile) {
            InnerTmpImageFile file1 = (InnerTmpImageFile) file;
            Image image1 = file1.getImage();
            if (image1 instanceof GPUImage) {
                return ((GPUImage) image1).getTexture();
            }
        }
        if (!async) throw new IllegalArgumentException("Non-async textures are not supported in Web");
        AsyncCacheData<ITexture2D> tex = TextureCache.INSTANCE.getLateinitTexture(file, timeout, true, (callback) -> {
            log("Reading image", file.getAbsolutePath());
            if (file instanceof WebRef2 || file instanceof BundledRef) {
                // call JS to generate a texture for us :)
                String path = file.getAbsolutePath();
                String prefix = BundledRef.PREFIX;
                if (path.startsWith(prefix)) {
                    path = getBaseURL() + path.substring(prefix.length());
                }
                Texture2D newTexture = new Texture2D(file.getName(), 1, 1, 1);
                loadTextureAsync(path, newTexture, callback);
            } else {
                System.err.println("Reading other images hasn't been implemented yet, '" +
                        file.getAbsolutePath() + "', '" + Object_toString(file) + "'");
                callback.err(new IOException("Reading other images hasn't been implemented yet"));
            }
            return Unit.INSTANCE;
        });
        return tex != null ? tex.getValue() : null;
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
    public static Pattern Pattern_compile_Ljava_lang_StringLjava_util_regex_Pattern(String str) {
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
    public static void MovingGrid_drawTextMesh_DIV(Object self, double baseSize, int factor) {
        // todo support text meshes, and implement this :)
    }

    @Alias(names = "me_anno_audio_openal_AudioManager_checkIsDestroyed_V")
    public static void AudioManager_checkIsDestroyed_V(Object self) {
        // cannot be destroyed, as far as I know :)
    }

    @Alias(names = "me_anno_gpu_OSWindow_addCallbacks_V")
    public static void OSWindow_addCallbacks_V(OSWindow self) {
        // not needed
    }

    @Alias(names = "me_anno_image_exr_EXRReader_read_Ljava_io_InputStreamLme_anno_image_Image")
    public static Object EXRReader_read_Ljava_io_InputStreamLme_anno_image_Image(InputStream stream) throws IOException {
        log("EXR is not supported!");
        stream.close();
        return null;
    }

    @Alias(names = "me_anno_image_exr_EXRReader_read_Ljava_nio_ByteBufferLme_anno_image_Image")
    public static Object EXRReader_read_Ljava_nio_ByteBufferLme_anno_image_Image(ByteBuffer buffer) {
        log("EXR is not supported!");
        return null;
    }

    // could be disabled, if it really is needed...
	/*@Alias(names = "me_anno_image_gimp_GimpImageXCompanion_readAsFolder_Lme_anno_io_files_FileReferenceLkotlin_jvm_functions_Function2V")
	public static void GimpImageXCompanion_readAsFolder(FileReference src, Function2<GimpImage, Exception, Unit> callback) {
		callback.invoke(null, new IOException("Gimp Image files are not supported in Web!"));
	}*/

    @Alias(names = "me_anno_image_gimp_GimpImageXCompanion_read_Ljava_io_InputStreamLme_anno_image_Image")
    public static Object GimpImageXCompanion_read_Ljava_io_InputStreamLme_anno_image_Image(InputStream stream) throws IOException {
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
    public static void Input_pollControllers_Lme_anno_gpu_OSWindowV(Object self, Object window) {
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
    public static Object Spellchecking_check_Ljava_lang_CharSequenceZZLjava_util_List(
            CharSequence t, boolean allowFirstLowerCase, boolean async) {
        return null;
    }

    @Alias(names = "me_anno_ui_input_components_CorrectingTextInput_getSuggestions_Ljava_util_List")
    public static Object CorrectingTextInput_getSuggestions_Ljava_util_List(Object self) {
        return null;
    }

    @NoThrow
    @JavaScript(code = "try { return BigInt(window.performance.memory.usedJSHeapSize); } catch(e){ return 0n; }")
    @Alias(names = "me_anno_ui_debug_JSMemory_jsUsedMemory_J")
    public static native long JSMemory_jsUsedMemory_J();

    @NoThrow
    @Alias(names = "me_anno_extensions_ExtensionLoader_loadInfoFromZip_Lme_anno_io_files_FileReferenceLme_anno_extensions_ExtensionInfo")
    public static Object ExtensionLoader_loadInfoFromZip_Lme_anno_io_files_FileReferenceLme_anno_extensions_ExtensionInfo(Object file) {
        // there is no Zip, extensions should be loaded directly ^^
        return null;
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_LogoKt_drawLogo_IIZZ")
    public static boolean LogoKt_drawLogo(int a, int b, boolean c) {
        return true;
    }

    @NoThrow
    @Alias(names = "me_anno_image_ImageCPUCache_get_Lme_anno_io_files_FileReferenceJZLme_anno_image_Image")
    public static Image ImageCPUCache_get_Lme_anno_io_files_FileReferenceJZLme_anno_image_Image(FileReference path, long timeout, boolean async) {
        // todo create image async using JavaScript
        log("Todo: create image async using JS", path.getAbsolutePath());
        return null;
    }

    @NoThrow
    @Alias(names = "me_anno_image_ImageCPUCache_get_Lme_anno_io_files_FileReferenceZLme_anno_image_Image")
    public static Image ImageCPUCache_get_Lme_anno_io_files_FileReferenceZLme_anno_image_Image(FileReference path, boolean async) {
        return ImageCPUCache_get_Lme_anno_io_files_FileReferenceJZLme_anno_image_Image(path, 10_000, async);
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_GFXBase_addCallbacks_Lme_anno_gpu_OSWindowV")
    public static void GFXBase_addCallbacks_Lme_anno_gpu_OSWindowV(Object window) {
        // we'll call it directly, no need for callbacks
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_GFXBase_close_Lme_anno_gpu_OSWindowV")
    public static void GFXBase_close_Lme_anno_gpu_OSWindowV(Object window) {
        // not really supported
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_GFXBase_loadRenderDoc_V")
    public static void GFXBase_loadRenderDoc_V() {
        // not supported
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_OSWindow_forceUpdateVsync_V")
    public static void OSWindow_forceUpdateVsync_V(Object self) {
        // not supported
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_OSWindow_toggleFullscreen_V")
    public static void OSWindow_toggleFullscreen_V(Object self) {
        disableCursor();
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_OSWindow_updateMousePosition_V")
    public static void OSWindow_updateMousePosition_V(Object self) {
        // done automatically
    }

    @NoThrow
    @Alias(names = "me_anno_gpu_GFXBase_handleClose_Lme_anno_gpu_OSWindowV")
    public static void GFXBase_handleClose_Lme_anno_gpu_OSWindowV(Object window) {
        // idc really, should not be callable
    }

    @NoThrow
    @Alias(names = "me_anno_input_Input_setClipboardContent_Ljava_lang_StringV")
    @JavaScript(code = "if(arg1) navigator.clipboard.writeText(str(arg1))")
    public static native void Input_setClipboardContent_Ljava_lang_StringV(Object self, String txt);

    @NoThrow
    @Alias(names = "me_anno_gpu_monitor_SubpixelLayout_detect_V")
    public static void SubpixelLayout_detect_V(Object self) {
        // todo implement this
        log("Todo: detect subpixel layout");
    }

    @NoThrow
    @Alias(names = "me_anno_extensions_ExtensionLoader_load_V")
    public static void ExtensionLoader_load_V() {
        // just skip for now 😄, 8k lines (out of 236k)
    }

    @Alias(names = "me_anno_utils_process_BetterProcessBuilder_start_Ljava_lang_Process")
    public static Process BetterProcessBuilder_start_Ljava_lang_Process(Object self) {
        throw new RuntimeException("Starting processes is not possible in WASM");
    }

    @Alias(names = "me_anno_utils_Sleep_waitForGFXThreadUntilDefined_ZLkotlin_jvm_functions_Function0_Ljava_lang_Object")
    public static Object waitUntilDefined(boolean killable, Object func) {
        throwJs("Cannot wait in Browser");
        return null;
    }

    @NoThrow
    @JavaScript(code = "" +
            "// console.log('running thread', str(arg1));\n" +
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
    private static void OfficialExtensions_register_V(Object self) {
    }

    @Alias(names = "org_apache_logging_log4j_LoggerImpl_print_Ljava_lang_StringLjava_lang_StringV")
    public static void LoggerImpl_print(LoggerImpl self, String prefix, String text) {
        // avoid printing line-by-line like the original, because JS saves the stack trace for each warning
        String line2 = "[" + LoggerImpl.Companion.getTimeStamp() + "," + prefix + ":" + self.getName() + "] " + text;
        LoggerImpl_printRaw(self, prefix, line2);
    }

    @NoThrow
    @Alias(names = "org_apache_logging_log4j_LoggerImpl_printRaw_Ljava_lang_StringLjava_lang_StringV")
    public static void LoggerImpl_printRaw(LoggerImpl self, String prefix, String line) {
        boolean justLog = !(Objects.equals(prefix, "ERR!") || Objects.equals(prefix, "WARN"));
        JavaLang.printString(line, justLog);
    }

    @NoThrow
    @Alias(names = "me_anno_maths_Maths_random_D")
    public static double Maths_random_D() {
        return ThreadLocalRandom.INSTANCE.nextDouble();
    }

    @NoThrow
    @JavaScript(code = "" +
            "for(let key in gl) { if(typeof(gl[key]) == 'number') {\n" +
            "   window.lib.GLNames_pushName(arg0, arg1, fill(arg0,key), gl[key]); }}")
    public static native void discoverGLNamesImpl(char[] buffer, HashMap<Integer, String> dst);

    @NoThrow
    @Alias(names = "me_anno_gpu_GLNames_discoverOpenGLNames_V")
    public static void GLNames_discoverOpenGLNames() throws NoSuchFieldException, IllegalAccessException {
        // engine will be shipped, so nothing to discover here
        // would be good for debugging though...
        Object dstNames0 = GLNames.class.getDeclaredField("glConstants").get(null);
        if (!(dstNames0 instanceof HashMap)) return;
        @SuppressWarnings("unchecked")
        HashMap<Integer, String> dstNames = (HashMap<Integer, String>) dstNames0;
        discoverGLNamesImpl(FillBuffer.getBuffer(), dstNames);
    }

    @Export
    @NoThrow
    @UsedIfIndexed
    @Alias(names = "GLNames_pushName")
    public static void GLNames_pushName(char[] buffer, HashMap<Integer, String> dst, int length, int value) {
        if (length <= 0) return;
        String glName = new String(buffer, 0, length);
        dst.put(Integer.valueOf(value), glName);
    }

    // todo why is Cold-LUT RenderMode still hanging???
    //  in the worst case, we'll have to replace TextureCache.getLUT
    /*@Alias(names = "me_anno_cache_AsyncCacheData_waitFor_Ljava_lang_Object")
    public static Object waitFor(AsyncCacheData<?> self) {
        if (self.getHasValue()) return self.getValue();
        throw new IllegalStateException("Cannot wait in Web");
    }

    @Alias(names = "me_anno_utils_Sleep_waitUntil_ZLkotlin_jvm_functions_Function0V")
    public static void waitUntil(boolean canBeKilled, Function0<Boolean> condition) {
        if (condition.invoke()) return;
        throw new IllegalStateException("Cannot wait in Web");
    }

    @Alias(names = "me_anno_utils_Sleep_waitUntilReturnWhetherIncomplete_ZJLkotlin_jvm_functions_Function0Z")
    public static boolean waitUntilReturnWhetherIncomplete(boolean canBeKilled, long timeoutNanos, Function0<Boolean> condition) {
        if (condition.invoke()) return false;
        throw new IllegalStateException("Cannot wait in Web");
    }*/
}
