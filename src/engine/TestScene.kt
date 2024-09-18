package engine

import jvm.JVM32.log
import me.anno.config.DefaultConfig.style
import me.anno.ecs.prefab.PrefabInspector
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.ecs.prefab.change.Path
import me.anno.engine.ui.ECSTreeView
import me.anno.engine.ui.EditorState
import me.anno.engine.ui.render.PlayMode
import me.anno.engine.ui.render.SceneView
import me.anno.engine.ui.scenetabs.ECSSceneTab
import me.anno.engine.ui.scenetabs.ECSSceneTabs
import me.anno.io.files.FileReference
import me.anno.ui.Panel
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.custom.CustomList
import me.anno.ui.editor.PropertyInspector

@Suppress("unused")
fun testScene(scene: PrefabSaveable, init: ((SceneView) -> Unit)? = null): Panel {
    scene.prefabPath = Path.ROOT_PATH
    return testScene(scene.ref, init)
}

fun testScene(scene: FileReference, init: ((SceneView) -> Unit)? = null): Panel {
    val listY = PanelListY(style)
    log("Created PanelListY")
    listY.add(ECSSceneTabs)
    log("Added ECSSceneTabs")
    val tab = ECSSceneTab(scene, PlayMode.EDITING)
    log("Created ECSSceneTab")
    // todo make this work, this stalls everything
    // ECSSceneTabs.open(tab, true)
    log("Opened ECSSceneTab")
    val sceneView = SceneView(PlayMode.EDITING, style)
    PrefabInspector.currentInspector = PrefabInspector(scene)
    log("Created PrefabInspector")
    val list = CustomList(false, style)
    log("Created CustomList")
    list.add(ECSTreeView(style), 1f)
    log("Created ECSTreeView")
    list.add(sceneView, 3f)
    log("Added sceneView")
    list.add(PropertyInspector({ EditorState.selection }, style), 1f)
    log("Added PropertyInspector")
    if (init != null) init(sceneView)
    log("Called init(sceneView)")
    // tryStartVR(sceneView)
    listY.add(list)
    list.weight = 1f
    listY.weight = 1f
    log("Finished UI")
    return listY
}