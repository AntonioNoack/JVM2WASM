package engine

import me.anno.engine.EngineBase
import me.anno.gpu.GFX
import me.anno.language.Language
import me.anno.language.translation.NameDesc
import me.anno.ui.Panel

class SimpleStudio(val panel: Panel) : EngineBase(NameDesc("Test"), 1, true) {
    override var language: Language = Language.AmericanEnglish // easier init than parent class
    override fun createUI() {
        val windowStack = GFX.someWindow.windowStack
        windowStack.push(panel)
    }
}