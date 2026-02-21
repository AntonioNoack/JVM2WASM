package engine

import jvm.NativeLog
import me.anno.fonts.Font
import me.anno.fonts.FontImpl
import me.anno.image.raw.IntImage

object TextGeneratorImpl : FontImpl<List<Font>>() {

    /*
    override fun generateASCIITexture(
        portableImages: Boolean,
        callback: Callback<Texture2DArray>,
        textColor: Int,
        backgroundColor: Int
    ) {
        val widthLimit: Int = maxTextureSize
        val heightLimit: Int = maxTextureSize

        // val offsetCache = getOffsetCache(font)
        val size = 0f//offsetCache.getOffset('w'.code, 'w'.code)
        val width = min(widthLimit, size.roundToInt() + 1)
        // leading + ascent + descent
        val height = min(heightLimit, font.sampleHeight)

        val simpleChars = simpleChars
        val tex = Texture2DArray("awtAtlas", width, height, simpleChars.size)

        tex.ensurePointer()
        bindTexture(tex.target, tex.pointer)
        val mask = (1 shl 24) - 1
        TextGen.genASCIITexture(
            font.name, font.size,
            simpleChars[0][0].code,  // letters are consecutive, so first letter is enough :)
            width, height, simpleChars.size,
            textColor and mask,
            backgroundColor and mask,
            1f + height / 1.3f
        )
        tex.afterUpload(GL11C.GL_RGBA8, 4, false)
        DebugGPUStorage.tex2da.add(tex)
        callback.ok(tex)
    }*/

    override fun getTextLength(font: Font, codepoint: Int): Int {
        return TextGen.measureTextSingle(font.name, font.size, codepoint)
    }

    override fun getTextLength(font: Font, codepointA: Int, codepointB: Int): Int {
        return TextGen.measureTextPair(font.name, font.size, codepointA, codepointB)
    }

    override fun drawGlyph(
        image: IntImage,
        x0: Int, x1: Int,
        y0: Int, y1: Int,
        strictBounds: Boolean,
        font: Font,
        fallbackFonts: List<Font>,
        fontIndex: Int,
        codepoint: Int,
        textColor: Int,
        backgroundColor: Int,
        portableImages: Boolean
    ) {
        NativeLog.log("drawGlyph($x0 - $x1, $y0 - $y1)")
        if (x1 > x0 && y1 > y0) TextGen.genASCIITexture(
            font.name, font.size, codepoint,
            x1 - x0, y1 - y0,
            textColor, backgroundColor,
            image.data, image.getIndex(x0, y0), image.stride,
        )
    }

    override fun getBaselineY(font: Font): Float = font.size * 0.73f
    override fun getLineHeight(font: Font): Float = font.size
    override fun getFallbackFonts(font: Font): List<Font> = emptyList()

    override fun getSupportLevel(
        fonts: List<Font>,
        codepoint: Int,
        lastSupportLevel: Int
    ): Int = 0
}