package engine

import me.anno.fonts.Font
import me.anno.fonts.FontImpl
import me.anno.image.raw.IntImage

object TextGeneratorImpl : FontImpl<List<Font>>() {

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
        if (x0 < 0 || y0 < 0) throw IllegalArgumentException("x0 and y0 must be >= 0")
        if (x1 > x0 && y1 > y0) TextGen.genASCIITexture(
            font.name, font.size, codepoint,
            x1 - x0, y1 - y0,
            image.width - x0, image.height - y0,
            textColor, backgroundColor,
            image.data, image.getIndex(x0, y0), image.stride,
            image.data.size
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