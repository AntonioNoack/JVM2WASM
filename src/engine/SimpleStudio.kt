package engine

import me.anno.gpu.GFX
import me.anno.language.Language
import me.anno.studio.StudioBase
import me.anno.ui.Panel
import me.anno.ui.Window

class SimpleStudio(val panel: Panel) : StudioBase("Test", 1, true) {
    override var language: Language = Language.AmericanEnglish // easier init than parent class
    override fun createUI() {
        val windowStack = GFX.someWindow!!.windowStack
        windowStack.add(Window(panel, false, windowStack))
    }
}