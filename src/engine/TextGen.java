package engine;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import me.anno.cache.CacheData;
import me.anno.cache.CacheSection;
import me.anno.cache.ICacheData;
import me.anno.fonts.AWTFont;
import me.anno.fonts.FontManager;
import me.anno.fonts.keys.TextCacheKey;
import me.anno.fonts.mesh.AlignmentGroup;
import me.anno.gpu.GFX;
import me.anno.gpu.drawing.DrawTexts;
import me.anno.gpu.drawing.GFXx2D;
import me.anno.gpu.texture.Clamping;
import me.anno.gpu.texture.GPUFiltering;
import me.anno.gpu.texture.Texture2D;
import me.anno.gpu.texture.Texture2DArray;
import org.lwjgl.opengl.GL11C;

import java.awt.*;
import java.awt.font.FontRenderContext;

import static engine.Engine.finishTexture;
import static engine.Engine.prepareTexture;
import static jvm.JVM32.b2i;

public class TextGen {

    @NoThrow
    @JavaScript(code = "measureText(arg0,arg1,str(arg2));return Math.ceil(tmp.width)")
    public static native int measureText1(String fontName, float fontSize, String text);

    private static final CacheSection TextSizeCache = new CacheSection("TextSize");

    private static int spaceBetweenLines(float fontSize) {
        return (int) (0.5f * fontSize + 0.5f);
    }

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

    @Alias(name = "me_anno_fonts_FontManager_getSize_Lme_anno_fonts_keys_TextCacheKeyI")
    public static int me_anno_fonts_FontManager_getSize_Lme_anno_fonts_keys_TextCacheKeyI(TextCacheKey key) {
        // calculate actual size
        // https://www.w3schools.com/tags/canvas_measuretext.asp
        // https://phychi.com/sho/render.js
        CacheData data = (CacheData) TextSizeCache.getEntry(key, 100_000, false, (it) -> {
            CharSequence text = it.getText();
            float fontSize = FontManager.INSTANCE.getAvgFontSize(it.fontSizeIndex());
            if (text.length() == 0) {
                return new CacheData<>(GFXx2D.INSTANCE.getSize(0, (int) fontSize));
            } else {
                int width = measureText1(it.getFontName(), fontSize, text.toString());
                int lineCount = countLines(text);
                int spaceBetweenLines = spaceBetweenLines(fontSize);
                int fontHeight = (int) fontSize;
                int height = calcTextHeight(fontHeight, lineCount, spaceBetweenLines);
                // val width = min(max(0, lineCount.maxOf { getStringWidth(getGroup(it)) }.roundToInt() + 1), GFX.maxTextureSize)
                // int height = measureText2();
                int mts = GFX.maxTextureSize;
                width = Math.min(it.getWidthLimit(), Math.min(width, mts));
                height = Math.min(it.getHeightLimit(), Math.min(height, mts));
                return new CacheData<>(GFXx2D.INSTANCE.getSize(width, height));
            }
        });
        assert data != null;
        return (int) data.getValue();
    }

    @NoThrow
    @JavaScript(code = "arg2=str(arg2);\n" +
            "measureText(arg0,arg1,arg2);\n" + // defines tmp
            "var w=Math.max(1,Math.min(arg3,Math.ceil(tmp.width))),h=arg4;\n" +
            "txtCanvas.width=w;txtCanvas.height=h;\n" +
            "ctx.fillStyle='#000'\n" +
            "ctx.fillRect(0,0,w,h)\n" +
            "ctx.fillStyle='#fff'\n" +
            "ctx.textAlign='center'\n" +
            "ctx.font=(arg1|0)+'px '+str(arg0);\n" +
            "ctx.fillText(arg2,w/2,1+h/1.3);\n" +
            "gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,ctx.getImageData(0,0,w,h).data);\n" +
            "return w;")
    private static native int genTexTexture(String font, float fontSize, String text, int wl, int h);

    @Alias(name = "me_anno_fonts_FontManagerXgetTextureX1_invoke_Lme_anno_fonts_keys_TextCacheKeyLme_anno_cache_ICacheData")
    public static ICacheData FontManagerXgetTextureX1_invoke_Lme_anno_fonts_keys_TextCacheKeyLme_anno_cache_ICacheData(Object lambda, TextCacheKey key) {

        // todo should return the same size as the texture will be...

        CharSequence text = key.getText();
        if (text.length() == 0) return null;

        // val font2 = getFont(key)
        int mts = GFX.maxTextureSize;
        float averageFontSize = FontManager.INSTANCE.getAvgFontSize(key.fontSizeIndex());

        int wl = key.getWidthLimit();
        wl = wl < 0 ? mts : Math.min(wl, mts);

        String text2 = text.toString();
        Texture2D tex = new Texture2D(text2, 0, 0, 1);
        prepareTexture(tex);

        float fontSize = FontManager.INSTANCE.getAvgFontSize(key.fontSizeIndex());
        int fontHeight = (int) fontSize;
        int lineCount = countLines(text);
        int spaceBetweenLines = spaceBetweenLines(fontSize);
        int h = calcTextHeight(fontHeight, lineCount, spaceBetweenLines);

        // generate and upload texture in JavaScript
        int w = genTexTexture(key.getFontName(), averageFontSize, text2, wl, h);
        finishTexture(tex, w, h, null);

        return tex;
    }

    @Alias(name = "me_anno_fonts_WebFonts_getFontMetrics_Ljava_awt_FontLjava_awt_FontMetrics")
    public static FontMetrics WebFonts_getFontMetrics(Font font) {
        return new FontMetrics(font) {
        };
    }

    @Alias(name = "me_anno_fonts_mesh_AlignmentGroup_getOffset_Ljava_awt_font_FontRenderContextIID")
    public static double AlignmentGroup_getOffset(AlignmentGroup group, FontRenderContext ctx, int charA, int charB) {
        // todo implement properly
        return group.getFont().getSize();
    }

    @NoThrow
    @JavaScript(code = "var w=arg3,h=arg4,d=arg5;\n" +
            "txtCanvas.width=w;txtCanvas.height=h*d;\n" +
            "ctx.fillStyle='#'+arg6.toString(16).padStart(6,'0');\n" +
            "ctx.fillRect(0,0,w,h)\n" +
            "ctx.fillStyle='#'+arg7.toString(16).padStart(6,'0');\n" +
            "ctx.textAlign='center'\n" +
            "ctx.font=(arg1|0)+'px '+str(arg0);\n" +
            "for(var i=0;i<d;i++) ctx.fillText(String.fromCharCode(arg2+i),w/2,arg8+h*i);\n" +
            "gl.texImage3D(gl.TEXTURE_2D_ARRAY,0,gl.RGBA8,w,h,d,0,gl.RGBA,gl.UNSIGNED_BYTE,ctx.getImageData(0,0,w,h*d).data);\n" +
            "return w;")
    private static native int genASCIITexture(
            String font, float fontSize, int text0, int width, int height, int depth,
            int textColor, int backgroundColor, float y0);

    @Alias(name = "me_anno_fonts_AWTFont_generateASCIITexture_ZIIILme_anno_gpu_texture_Texture2DArray")
    public static Texture2DArray generateASCIITexture(AWTFont self, boolean portableImages, int textColor, int backgroundColor, int extraPadding) {
        final int widthLimit = GFX.maxTextureSize;
        final int heightLimit = GFX.maxTextureSize;

        final FontRenderContext ctx = new FontRenderContext(null, true, true);
        final AlignmentGroup alignment = AlignmentGroup.Companion.getAlignments(self);
        final double size = alignment.getOffset(ctx, 'w', 'w');
        final int width = Math.min(widthLimit, (int) Math.round(size) + 1 + 2 * extraPadding);
        final int height = Math.min(heightLimit, WebFonts_getFontMetrics(self.getFont()).getHeight() + 2 * extraPadding);

        final String[] simpleChars = DrawTexts.INSTANCE.getSimpleChars();
        final Texture2DArray tex = new Texture2DArray("awtAtlas", width, height, simpleChars.length);

        tex.ensurePointer();
        Texture2D.Companion.bindTexture(tex.getTarget(), tex.getPointer());
        final int mask = (1 << 24) - 1;
        genASCIITexture(
                self.getName(), self.getSize(), simpleChars[0].charAt(0), // letters are consecutive, so first letter is enough :)
                width, height, simpleChars.length,
                textColor & mask, backgroundColor & mask,
                1f + height / 1.3f + extraPadding
        );

        final long size1 = ((long) width * height * simpleChars.length) << 2;
        tex.setLocallyAllocated(Texture2D.Companion.allocate(tex.getLocallyAllocated(), size1));
        tex.setInternalFormat(GL11C.GL_RGBA8);
        tex.setCreated(true);
        tex.filtering(GPUFiltering.TRULY_NEAREST);
        tex.clamping(Clamping.CLAMP);

        return tex;
    }

}
