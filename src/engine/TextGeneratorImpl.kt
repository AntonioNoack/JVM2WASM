package engine

import me.anno.fonts.Font
import me.anno.fonts.TextGenerator
import me.anno.fonts.mesh.CharacterOffsetCache.Companion.getOffsetCache
import me.anno.gpu.GFX.maxTextureSize
import me.anno.gpu.debug.DebugGPUStorage
import me.anno.gpu.drawing.DrawTexts.simpleChars
import me.anno.gpu.texture.ITexture2D
import me.anno.gpu.texture.Texture2D.Companion.bindTexture
import me.anno.gpu.texture.Texture2DArray
import me.anno.utils.async.Callback
import org.lwjgl.opengl.GL11C
import kotlin.math.min
import kotlin.math.roundToInt

class TextGeneratorImpl(private val font: Font) : TextGenerator {

    override fun calculateSize(text: CharSequence, widthLimit: Int, heightLimit: Int): Int {
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

        val offsetCache = getOffsetCache(font)
        val size = offsetCache.getOffset('w'.code, 'w'.code)
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
    }
}