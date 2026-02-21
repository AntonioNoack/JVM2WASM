package engine;

import annotations.JavaScriptWASM;
import annotations.NoThrow;
import annotations.JavaScriptNative;

public class TextGen {

    @NoThrow
    @JavaScriptWASM(code = "measureText(arg0,arg1,str(arg2));return Math.ceil(tmp.width)")
    public static native int measureTextFull(String fontName, float fontSize, String text);

    @NoThrow
    @JavaScriptWASM(code = "measureText(arg0,arg1,String.fromCodePoint(arg2));return Math.ceil(tmp.width)")
    public static native int measureTextSingle(String fontName, float fontSize, int c1);

    @NoThrow
    @JavaScriptWASM(code = "measureText(arg0,arg1,String.fromCodePoint(arg2)+String.fromCodePoint(arg3));return Math.ceil(tmp.width)")
    public static native int measureTextPair(String fontName, float fontSize, int c1, int c2);

    /*@NoThrow
    private static int spaceBetweenLines(float fontSize) {
        return (int) (0.5f * fontSize + 0.5f);
    }

    @NoThrow
    private static int calcTextHeight(int fontHeight, int lineCount, int spaceBetweenLines) {
        return fontHeight * lineCount + (lineCount - 1) * spaceBetweenLines;
    }

    private static int countLines(CharSequence text) {
        int lineCount = 1;
        for (int i = 0, l = text.length(); i < l; i++) {
            lineCount += b2i(text.charAt(i) == '\n');
        }
        return lineCount;
    }*/

    /*@Alias(names = "me_anno_fonts_FontManager_getSize_Lme_anno_fonts_keys_TextCacheKeyI")
    public static int getSize(String fontName, CharSequence text, float fontSize, int widthLimit, int heightLimit) {
        // calculate actual size
        // https://www.w3schools.com/tags/canvas_measuretext.asp
        // https://phychi.com/sho/render.js
        if (text.length() == 0) {
            return GFXx2D.INSTANCE.getSize(0, (int) fontSize);
        } else {
            int width = measureTextFull(fontName, fontSize, text.toString());
            int lineCount = countLines(text);
            int spaceBetweenLines = spaceBetweenLines(fontSize);
            int fontHeight = (int) fontSize;
            int height = calcTextHeight(fontHeight, lineCount, spaceBetweenLines);
            // val width = min(max(0, lineCount.maxOf { getStringWidth(getGroup(it)) }.roundToInt() + 1), GFX.maxTextureSize)
            // int height = measureText2();
            int mts = GFX.maxTextureSize;
            width = Math.min(widthLimit, Math.min(width, mts));
            height = Math.min(heightLimit, Math.min(height, mts));
            return GFXx2D.INSTANCE.getSize(width, height);
        }
    }*/

    static final String commonCode = "" +
            "let font=arg0,w=arg3,h=arg4,dw=arg5,dh=arg6,color0Int=arg8,color1Int=arg7;\n" +
            "let dstArray=arg9, dstOffset=arg10, dstStride=arg11;\n" +
            "txtCanvas.width=w;txtCanvas.height=h;\n" +
            "let color0 = '#'+color0Int.toString(16).padStart(6,'0');\n" +
            "let color1 = '#'+color1Int.toString(16).padStart(6,'0');\n" +
            "ctx.textAlign='center'\n" +
            "ctx.font=(arg1|0)+'px '+str(font);\n" +

            "ctx.fillStyle=color0;\n" + // clear space, just in case
            "ctx.fillRect(0,0,w,h);\n" +
            "ctx.fillStyle=color1;\n" +
            // todo what is the correct y???
            "ctx.fillText(String.fromCharCode(arg2),w/2,arg1*0.5);\n" +
            "let buffer = ctx.getImageData(0,0,w,h).data;\n" +
            "let readPtr = 0;\n";

    @NoThrow
    @JavaScriptWASM(code = "" +
            commonCode +
            "for(let y=0;y<Math.min(h,dh);y++){\n" +
            "   let dstPtr = dstArray + arrayOverhead + ((dstOffset+dstStride*y)<<2);\n" +
            "   for(let x=0,len=Math.min(w,dw)*4;x<len;x++){\n" +
            // todo test this function with WASM target
            // todo optimized, direct copy
            "       window.lib.w8(dstPtr++, buffer[readPtr++]);\n" +
            "   }\n" +
            "}\n")
    @JavaScriptNative(code = "" +
            commonCode +
            "for(let y=0;y<Math.min(h,dh);y++){\n" +
            "   let dstPtr = dstOffset+dstStride*y;\n" +
            "   for(let x=0,len=Math.min(w,dw);x<len;x++){\n" +
            "       dstArray.values[dstPtr++] = " +
            "(buffer[readPtr++] << 24) | " +
            "(buffer[readPtr++] << 16) | " +
            "(buffer[readPtr++] << 8) | " +
            "(buffer[readPtr++]);\n" +
            "   }\n" +
            "}\n")
    public static native void genASCIITexture(
            String font, float fontSize, int text0,
            int width, int height,
            int imgWidth, int imgHeight,
            int textColor, int backgroundColor,
            int[] dstPointer, int dstOffset, int stride,
            int arraySize);
}
