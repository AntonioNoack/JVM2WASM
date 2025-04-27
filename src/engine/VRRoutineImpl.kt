package engine

import engine.VRRoutineJS.*
import me.anno.engine.EngineBase
import me.anno.engine.ui.render.RenderView
import me.anno.engine.ui.vr.VRRendering
import me.anno.engine.ui.vr.VRRenderingRoutine
import me.anno.gpu.GFXState.useFrame
import me.anno.gpu.OSWindow
import me.anno.gpu.RenderStep.callOnGameLoop
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.framebuffer.TargetType
import me.anno.gpu.texture.ITexture2D
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.max

class VRRoutineImpl : VRRendering(), VRRenderingRoutine {

    lateinit var rv: RenderView

    private val matrixTmp = FloatArray(16)
    private val viewport = IntArray(4)

    private val modelMatrix = Matrix4f()
    private val projectionMatrix = Matrix4f()

    private val framebuffer = Framebuffer("VR", 1024, 720, TargetType.UInt8x4)

    override fun accumulateViewTransforms(): Int {
        val numViews = getNumViews()
        for (viewIndex in 0 until numViews) {
            fillModelMatrix(viewIndex, matrixTmp)
            modelMatrix.set(matrixTmp)
            modelMatrix.getTranslation(tmpPos)
            modelMatrix.getUnnormalizedRotation(tmpRot)
            position.add(tmpPos)
            rotation.add(tmpRot)
        }
        return numViews
    }

    // framebuffer was prepared inside ensureFBSize()
    override fun setupFramebuffer(
        viewIndex: Int, width: Int, height: Int,
        colorTextureI: Int, depthTextureI: Int
    ): Framebuffer = framebuffer

    override var isActive: Boolean = false
    override val leftView: Vector4f = Vector4f(0.5f, 1f, -0.25f, 0f)
    override val rightView: Vector4f = Vector4f(0.5f, 1f, 0.25f, 0f)
    override val leftTexture: ITexture2D? get() = if (getNumViews() > 0) framebuffer.getTexture0() else null
    override val rightTexture: ITexture2D? get() = if (getNumViews() > 0) framebuffer.getTexture0() else null
    override val previewGamma: Float = 1f

    override fun startSession(window: OSWindow, rv: RenderView): Boolean {
        this.rv = rv
        isActive = true
        return true
    }

    private val tmpPos = Vector3f()
    private val tmpRot = Quaternionf()

    private fun ensureFBSize(numViews: Int) {
        var maxWidth = 0
        var maxHeight = 0

        for (viewIndex in 0 until numViews) {
            fillViewport(viewIndex, viewport)
            maxWidth = max(maxWidth, viewport[0] + viewport[2])
            maxHeight = max(maxHeight, viewport[1] + viewport[3])
        }

        if (maxWidth > 0 && maxHeight > 0) {
            val framebuffer = framebuffer
            framebuffer.ensureSize(maxWidth, maxHeight, 1)
        }
    }

    private fun renderView(viewIndex: Int) {
        fillModelMatrix(viewIndex, matrixTmp)
        modelMatrix.set(matrixTmp)
        modelMatrix.getTranslation(tmpPos)
        modelMatrix.getUnnormalizedRotation(tmpRot)

        fillProjectionMatrix(viewIndex, matrixTmp)
        projectionMatrix.set(matrixTmp)

        fillViewport(viewIndex, viewport)

        renderFrame(
            rv, viewIndex,
            viewport[0], viewport[1], viewport[2], viewport[3],
            0, 0, tmpPos, tmpRot, projectionMatrix
        )
    }

    override fun drawFrame(window: OSWindow): Boolean {

        val rv = rv
        beginRenderViews(rv, framebuffer.width, framebuffer.height)

        val numViews = getNumViews()
        if (numViews > 0) {

            ensureFBSize(numViews)

            for (viewIndex in 0 until numViews) {
                renderView(viewIndex)
            }

            useFrame(framebuffer) {
                drawToTargetFramebuffer(framebuffer.pointer, framebuffer.width, framebuffer.height)
            }
        }

        callOnGameLoop(EngineBase.instance!!, window)
        return true
    }

    override fun setRenderView(rv: RenderView) {
        this.rv = rv
    }
}