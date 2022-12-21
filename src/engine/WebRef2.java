package engine;

import annotations.*;
import kotlin.NotImplementedError;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import me.anno.io.files.FileReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class WebRef2 extends FileReference {

    public WebRef2(String absolutePath) {
        super(absolutePath);
    }

    // todo check if it actually exists... cannot be done sync
    // todo -> bake in list of all existing things...
    @Override
    public boolean getExists() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public long getLastAccessed() {
        return 0;
    }

    @Override
    public long getLastModified() {
        return 0;
    }

    @Override
    public boolean delete() {
        return false;
    }

    @NotNull
    @Override
    public FileReference getChild(@NotNull String s) {
        return new WebRef2(getAbsolutePath() + '/' + s);
    }

    private FileReference parent;

    @Nullable
    @Override
    public FileReference getParent() {
        String path = getAbsolutePath();
        int lix = path.lastIndexOf('/');
        if (lix < 0) return null;
        if (parent == null) parent = new WebRef2(path.substring(0, lix));
        return parent;
    }

    @NoThrow
    @JavaScript(code = "" +
            "var x = new XMLHttpRequest();\n" +
            // todo implement args properly
            "var url = str(arg0);\n" +
            "var args={};\n" +
            "jsRefs[arg1]=1;\n" +
            "x.open('POST', url);\n" +
            "x.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');\n" +
            "x.onreadystatechange = function(){\n" +
            "   if(x.readyState==4){\n" +
            "       if(x.status==200){\n" +
            "           var data = x.response;\n" +
            "           var dst = window.lib.createBytes(data.length);\n" +
            "           if(dst[1]) throw dst[1];\n" +
            "           dst = dst[0];\n" +
            "           const byteArray = new Uint8Array(data);\n" +
            "           byteArray.forEach((element, index) => {\n" +
            "               window.lib.w8(dst+arrayOverhead+index, element);\n" +
            "           });\n" +
            "           var dst2 = window.lib.createString(dst);\n" +
            "           if(dst2[1]) throw dst2[1];\n" +
            "           window.lib.invoke2(arg1, dst2[0], 0);\n" +
            "       } else {\n" +
            "           window.lib.invoke2(arg1, 0, 0);\n" +
            "       }\n" +
            // todo create and return exception
            "       delete jsRefs[arg1];\n" +
            "   }\n" +
            "}\n" +
            "x.send(args);")
    private static native void readText(String url, Object callback);

    @NoThrow
    @JavaScript(code = "" +
            "var x = new XMLHttpRequest();\n" +
            // todo implement args properly
            "var url = str(arg0);\n" +
            "var args={};\n" +
            "jsRefs[arg1]=1;\n" +
            "x.open('POST', url);\n" +
            "x.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');\n" +
            "x.onreadystatechange = function(){\n" +
            "   if(x.readyState==4){\n" +
            "       if(x.status==200){\n" +
            "           var data = x.response;\n" +
            "           var dst = window.lib.createBytes(data.length);\n" +
            "           if(dst[1]) throw dst[1];\n" +
            "           dst = dst[0];\n" +
            "           const byteArray = new Uint8Array(data);\n" +
            "           byteArray.forEach((element, index) => {\n" +
            "               window.lib.w8(dst+arrayOverhead+index, element);\n" +
            "           });\n" +
            "           window.lib.invoke2(arg1, dst, 0);\n" +
            "       } else {\n" +
            // todo create and return exception
            "           window.lib.invoke2(arg1, 0, 0);\n" +
            "       }\n" +
            "       delete jsRefs[arg1];\n" +
            "   }\n" +
            "}\n" +
            "x.send(args);")
    private static native void readBytes(String url, Object callback);

    @NoThrow
    @JavaScript(code = "" +
            "var x = new XMLHttpRequest();\n" +
            // todo implement args properly
            "var url = str(arg0);\n" +
            "var args={};\n" +
            "jsRefs[arg1]=1;\n" +
            "x.open('POST', url);\n" +
            "x.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');\n" +
            "x.onreadystatechange = function(){\n" +
            "   if(x.readyState==4){\n" +
            "       if(x.status==200){" +
            "           var data = x.response;\n" +
            "           var dst = window.lib.createBytes(data.length);\n" +
            "           if(dst[1]) throw dst[1];\n" +
            "           dst = dst[0];\n" +
            "           var dst2 = window.lib.createByteStream(dst);\n" +
            "           if(dst2[1]) throw dst2[1];\n" +
            "           dst2 = dst2[0];\n" +
            "           const byteArray = new Uint8Array(data);\n" +
            "           byteArray.forEach((element, index) => {\n" +
            "               window.lib.w8(dst+arrayOverhead+index, element);\n" +
            "           });\n" +
            "           window.lib.invoke2(arg1, dst2, 0);" +
            "       } else {\n" +
            // invoke exception if status is not 200
            // todo create and return exception
            "           window.lib.invoke2(arg1, 0, 0);" +
            "       }\n" +
            "       delete jsRefs[arg1];\n" +
            "   }\n" +
            "}\n" +
            "x.send(args);")
    private static native void readStream(String url, Object callback);

    @Export
    @UsedIfIndexed
    @Alias(name = "invoke2")
    static <A, B> void invoke2(Function2<A, B, Unit> function, A arg1, B arg2) {
        function.invoke(arg1, arg2);
    }

    @Export
    @UsedIfIndexed
    @Alias(name = "createBytes")
    public static byte[] createBytes(int length) {
        return new byte[length];
    }

    @Export
    @UsedIfIndexed
    @Alias(name = "createString")
    public static String createString(byte[] bytes) {
        if (bytes == null) return null;
        return new String(bytes);
    }

    @Export
    @UsedIfIndexed
    @Alias(name = "createByteStream")
    public static ByteArrayInputStream createByteStream(byte[] bytes) {
        if (bytes == null) return null;
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void inputStream(long l, @NotNull Function2<? super InputStream, ? super Exception, Unit> callback) {
        if (false) invoke2(null, createByteStream(createBytes(0)), null); // ignore
        readStream(getAbsolutePath(), callback);
    }

    @Override
    public void readText(@NotNull Function2<? super String, ? super Exception, Unit> callback) {
        if (false) invoke2(null, createString(createBytes(0)), null); // ignore
        readText(getAbsolutePath(), callback);
    }

    @Override
    public void readText(@NotNull Charset charset, @NotNull Function2<? super String, ? super Exception, Unit> callback) {
        FileReference ref = this;
        ref.readText(callback);
    }

    @Override
    public void readBytes(@NotNull Function2<? super byte[], ? super Exception, Unit> callback) {
        if (false) invoke2(null, createBytes(0), null);
        readBytes(getAbsolutePath(), callback);
    }

    @NotNull
    @Override
    public InputStream inputStreamSync() throws IOException {
        throw new IOException("Web cannot request files synchronously");
    }

    @NotNull
    @Override
    public String readTextSync() throws IOException {
        throw new IOException("Web cannot request files synchronously");
    }

    @NotNull
    @Override
    public byte[] readBytesSync() throws IOException {
        throw new IOException("Web cannot request files synchronously");
    }

    @NotNull
    @Override
    public ByteBuffer readByteBufferSync(boolean b) throws IOException {
        throw new IOException("Web cannot request files synchronously");
    }

    @Override
    public long length() throws IOException {
        return -1;
    }

    @Override
    public boolean mkdirs() throws IOException {
        throw new IOException("Cannot write to web");
    }

    @NotNull
    @Override
    public OutputStream outputStream(boolean b) throws IOException {
        throw new IOException("Cannot write to web");
    }

    @Override
    public boolean renameTo(@NotNull FileReference fileReference) throws IOException {
        throw new IOException("Cannot write to web");
    }

    @NotNull
    @Override
    public URI toUri() {
        // todo this is using sun -> make it work :)
        // new InputStreamReader()
        throw new NotImplementedError();
    }

}
