package com.seijind.stringsmith.extract

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.seijind.stringsmith.settings.StringSmithSettings
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

object CmpModuleUtil {

    fun findResPackage(
        project: Project,
        file: VirtualFile,
        ktFile: KtFile?,
        settings: StringSmithSettings = StringSmithSettings.getInstance()
    ): String? {
        settings.cmpResPackageOverride.trim().takeIf { it.isNotEmpty() }?.let { return it }

        val moduleRoot = ModuleRootUtil.findModuleRoot(file)
        val gradleText = moduleRoot?.let { ModuleRootUtil.readGradleText(it) }
        val filePackage = ktFile?.packageFqName?.asString().orEmpty()
        val moduleNamespace = gradleText?.let { AndroidModuleText.parseNamespaces(it).firstOrNull() }

        return CmpModuleText.resolveResPackage(
            gradleText = gradleText,
            existingResImportPackages = cachedResImportPackages(project),
            filePackage = filePackage,
            moduleNamespace = moduleNamespace
        )
    }

    /** Project-wide scan (cached, invalidated on PSI change) for `*.generated.resources` packages already imported. */
    private fun cachedResImportPackages(project: Project): List<String> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(
                scanResImportPackages(project),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }

    private fun scanResImportPackages(project: Project): List<String> {
        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)
        val result = linkedSetOf<String>()
        for (vf in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            val ktFile = psiManager.findFile(vf) as? KtFile ?: continue
            for (imp in ktFile.importDirectives) {
                val fq = imp.importedFqName?.asString() ?: continue
                if (fq.endsWith(".generated.resources.Res")) {
                    result += fq.removeSuffix(".Res")
                }
            }
        }
        return result.toList()
    }
}
