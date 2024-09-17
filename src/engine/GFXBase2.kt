package engine

import me.anno.gpu.GFX
import me.anno.gpu.OSWindow
import me.anno.gpu.RenderStep

fun renderFrame2(window: OSWindow) {
    // GLFWController.pollControllers(window)
    GFX.activeWindow = window
    RenderStep.renderStep(window, true)
}
