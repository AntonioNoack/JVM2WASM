package engine

import me.anno.fonts.Font
import me.anno.fonts.FontImpl
import me.anno.image.raw.IntImage

object TextGeneratorImpl : FontImpl<Font>() {

    /*override fun calculateSize(text: CharSequence, widthLimit: Int, heightLimit: Int): Int {
        return TextGen.getSize(font.name, text, font.size, widthLimit, heightLimit)
    }

    override fun getBaselineY(): Float {
        return font.size * 0.8f // good like that?
    }

    override fun getLineHeight(): Float {
        return font.size
    }

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
    }

    override fun generateTexture(
        text: CharSequence,
        widthLimit: Int,
        heightLimit: Int,
        portableImages: Boolean,
        callback: Callback<ITexture2D>,
        textColor: Int,
        backgroundColor: Int
    ) {
        callback.ok(TextGen.generateTexture(font.name, text, font.size, widthLimit))
    }*/

    override fun getTextLength(font: Font, codepoint: Int): Int {
        TODO("Not yet implemented")
    }

    override fun getTextLength(font: Font, codepointA: Int, codepointB: Int): Int {
        TODO("Not yet implemented")
    }

    override fun drawGlyph(
        image: IntImage,
        x0: Int,
        x1: Int,
        y0: Int,
        y1: Int,
        strictBounds: Boolean,
        font: Font,
        fallbackFonts: Font,
        fontIndex: Int,
        codepoint: Int,
        textColor: Int,
        backgroundColor: Int,
        portableImages: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override fun getBaselineY(font: Font): Float {
        TODO("Not yet implemented")
    }

    override fun getLineHeight(font: Font): Float {
        TODO("Not yet implemented")
    }

    override fun getFallbackFonts(font: Font): Font {
        TODO("Not yet implemented")
    }

    override fun getSupportLevel(
        fonts: Font,
        codepoint: Int,
        lastSupportLevel: Int
    ): Int {
        TODO("Not yet implemented")
    }
}