package engine

import me.anno.fonts.Font
import me.anno.fonts.TextGenerator
import me.anno.fonts.mesh.CharacterOffsetCache.Companion.getOffsetCache
import me.anno.gpu.GFX.maxTextureSize
import me.anno.gpu.drawing.DrawTexts.simpleChars
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.gpu.texture.ITexture2D
import me.anno.gpu.texture.Texture2D.Companion.allocate
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

    override fun generateASCIITexture(
        portableImages: Boolean,
        callback: Callback<Texture2DArray>,
        textColor: Int,
        backgroundColor: Int,
        extraPadding: Int
    ) {
        val widthLimit: Int = maxTextureSize
        val heightLimit: Int = maxTextureSize

        val alignment = getOffsetCache(font)
        val size = alignment.getOffset('w'.code, 'w'.code)
        val width = min(widthLimit, size.roundToInt() + 1 + 2 * extraPadding)
        // leading + ascent + descent
        val height = min(heightLimit, font.sampleHeight + 2 * extraPadding)

        val simpleChars = simpleChars
        val tex = Texture2DArray("awtAtlas", width, height, simpleChars.size)

        tex.ensurePointer()
        bindTexture(tex.target, tex.pointer)
        val mask = (1 shl 24) - 1
        TextGen.genASCIITexture(
            font.name,
            font.size,
            simpleChars[0][0].code,  // letters are consecutive, so first letter is enough :)
            width,
            height,
            simpleChars.size,
            textColor and mask,
            backgroundColor and mask,
            1f + height / 1.3f + extraPadding
        )

        val size1 = width.toLong() * height * simpleChars.size shl 2
        tex.locallyAllocated = allocate(tex.locallyAllocated, size1)
        tex.internalFormat = GL11C.GL_RGBA8
        tex.wasCreated = true
        tex.filtering(Filtering.TRULY_NEAREST)
        tex.clamping(Clamping.CLAMP)
        callback.ok(tex)
    }

    override fun generateTexture(
        text: CharSequence,
        widthLimit: Int,
        heightLimit: Int,
        portableImages: Boolean,
        callback: Callback<ITexture2D>,
        textColor: Int,
        backgroundColor: Int,
        extraPadding: Int
    ) {
        TextGen.generateTexture(font.name, text, font.size, widthLimit)
    }
}