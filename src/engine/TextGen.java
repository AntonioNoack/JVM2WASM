package engine;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import me.anno.gpu.GFX;
import me.anno.gpu.drawing.GFXx2D;
import me.anno.gpu.texture.ITexture2D;
import me.anno.gpu.texture.Texture2D;

import static engine.Engine.finishTexture;
import static engine.Engine.prepareTexture;
import static jvm.JVMShared.b2i;

public class TextGen {

    @NoThrow
    @JavaScript(code = "measureText(arg0,arg1,str(arg2));return Math.ceil(tmp.width)")
    public static native int measureText1(String fontName, float fontSize, String text);

    @NoThrow
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
    }

    @Alias(names = "me_anno_fonts_FontManager_getSize_Lme_anno_fonts_keys_TextCacheKeyI")
    public static int getSize(String fontName, CharSequence text, float fontSize, int widthLimit, int heightLimit) {
        // calculate actual size
        // https://www.w3schools.com/tags/canvas_measuretext.asp
        // https://phychi.com/sho/render.js
        if (text.length() == 0) {
            return GFXx2D.INSTANCE.getSize(0, (int) fontSize);
        } else {
            int width = measureText1(fontName, fontSize, text.toString());
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
    }

    @NoThrow
    @JavaScript(code = "arg2=str(arg2);\n" +
            "measureText(arg0,arg1,arg2);\n" + // defines tmp
            "let w=Math.max(1,Math.min(arg3,Math.ceil(tmp.width))),h=arg4;\n" +
            "txtCanvas.width=w;txtCanvas.height=h;\n" +
            "ctx.fillStyle='#000'\n" +
            "ctx.fillRect(0,0,w,h)\n" +
            "ctx.fillStyle='#fff'\n" +
            "ctx.textAlign='center'\n" +
            "ctx.font=(arg1|0)+'px '+str(arg0);\n" +
            "ctx.fillText(arg2,w/2,1+h/1.3);\n" +
            "gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,ctx.getImageData(0,0,w,h).data);\n" +
            "return w;")
    public static native int genTexTexture(String font, float fontSize, String text, int wl, int h);

    public static ITexture2D generateTexture(String fontName, CharSequence text, float fontSize, int wl) {

        // todo should return the same size as the texture will be...

        if (text.length() == 0) return null;

        // val font2 = getFont(key)
        int mts = GFX.maxTextureSize;

        wl = wl < 0 ? mts : Math.min(wl, mts);

        String text2 = text.toString();
        Texture2D tex = new Texture2D(text2, 0, 0, 1);
        prepareTexture(tex);

        int fontHeight = (int) fontSize;
        int lineCount = countLines(text);
        int spaceBetweenLines = spaceBetweenLines(fontSize);
        int h = calcTextHeight(fontHeight, lineCount, spaceBetweenLines);

        // generate and upload texture in JavaScript
        int w = genTexTexture(fontName, fontSize, text2, wl, h);
        finishTexture(tex, w, h, null);

        return tex;
    }

    @NoThrow
    @JavaScript(code = "let w=arg3,h=arg4,d=arg5;\n" +
            "txtCanvas.width=w;txtCanvas.height=h*d;\n" +
            "let color0 = '#'+arg7.toString(16).padStart(6,'0');\n" +
            "let color1 = '#'+arg6.toString(16).padStart(6,'0');\n" +
            "ctx.textAlign='center'\n" +
            "ctx.font=(arg1|0)+'px '+str(arg0);\n" +
            "for(let i=0;i<d;i++) {\n" +
            "   ctx.fillStyle=color0;\n" + // clear space, just in case
            "   ctx.fillRect(0,i*h,w,h);\n" +
            "   ctx.fillStyle=color1;\n" +
            "   ctx.fillText(String.fromCharCode(arg2+i),w/2,arg8+h*i);\n" +
            "}\n" +
            "let buffer = ctx.getImageData(0,0,w,h*d).data;\n" +
            "gl.texImage3D(gl.TEXTURE_2D_ARRAY,0,gl.RGBA8,w,h,d,0,gl.RGBA,gl.UNSIGNED_BYTE,buffer);\n")
    public static native void genASCIITexture(
            String font, float fontSize, int text0, int width, int height, int depth,
            int textColor, int backgroundColor, float y0);
}
