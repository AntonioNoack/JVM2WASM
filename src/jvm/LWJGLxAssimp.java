package jvm;

import annotations.Alias;
import me.anno.io.files.FileReference;
import org.lwjgl.assimp.AIScene;

public class LWJGLxAssimp {

    @Alias(name = "me_anno_mesh_assimp_StaticMeshesLoader_loadFile_Lme_anno_io_files_FileReferenceILorg_lwjgl_assimp_AIScene")
    public static AIScene StaticMeshesLoader_loadFile(Object self, FileReference ref, int flags) {
        throw new IllegalStateException("Assimp is not supported!");
    }

    @Alias(name = "me_anno_mesh_assimp_AnimatedMeshesLoader_readAsFolder2_Lme_anno_io_files_FileReferenceLme_anno_io_files_FileReferenceILkotlin_Pair")
    public static Object AnimatedMeshesLoader_readAsFolder2(Object self, FileReference ref, FileReference folder, int flags) {
        throw new IllegalStateException("Assimp is not supported!");
    }

    @Alias(name = "me_anno_mesh_assimp_AnimatedMeshesLoader_readAsFolder_Lme_anno_io_files_FileReferenceLme_anno_io_files_FileReferenceILme_anno_io_zip_InnerFolder")
    public static Object AnimatedMeshesLoader_readAsFolder(Object self, FileReference ref, FileReference ref2, int flags) {
        throw new IllegalStateException("Assimp is not supported!");
    }
}
