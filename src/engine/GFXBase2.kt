package engine

import me.anno.gpu.GFX
import me.anno.gpu.OSWindow
import me.anno.input.Input

fun renderFrame2(window: OSWindow) {
    Input.pollControllers(window)
    GFX.activeWindow = window
    GFX.renderStep(window, true)
}
