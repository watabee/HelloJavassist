package jp.watabee.logger

import com.android.SdkConstants
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.DefaultContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.BaseExtension
import javassist.ClassPool
import javassist.Modifier
import org.gradle.api.Project
import java.io.File
import java.util.EnumSet

class LoggerTransformer(private val project: Project) : Transform() {

    override fun getName(): String = "logger"

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> =
        mutableSetOf(DefaultContentType.CLASSES)

    override fun isIncremental(): Boolean = false

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> =
        EnumSet.of(Scope.PROJECT)

    override fun getReferencedScopes(): MutableSet<in QualifiedContent.Scope> =
        EnumSet.of(Scope.EXTERNAL_LIBRARIES, Scope.SUB_PROJECTS, Scope.TESTED_CODE)

    override fun transform(transformInvocation: TransformInvocation) {
        super.transform(transformInvocation)

        val log = project.logger
        val outputDir = transformInvocation.outputProvider.getContentLocation(
            name,
            outputTypes,
            scopes,
            Format.DIRECTORY
        )

        val classPool = createClassPool(transformInvocation)
        val ctClasses =
            collectClassNames(transformInvocation).map { className ->
                log.error("className: $className")
                classPool.get(className)
            }

        ctClasses.flatMap { it.declaredMethods.toList() }
            .filter { !Modifier.isAbstract(it.modifiers) && !Modifier.isNative(it.modifiers) }
            .forEach {
                log.error("methodName: ${it.name}")

                it.insertBefore("android.util.Log.e(\"LoggerTransformer\", \"before: ${it.name}\");")
                it.insertAfter("android.util.Log.e(\"LoggerTransformer\", \"after: ${it.name}\");")
            }

        ctClasses.forEach { it.writeFile(outputDir.canonicalPath) }
    }

    private fun createClassPool(invocation: TransformInvocation): ClassPool {
        val classPool = ClassPool(null)
        classPool.appendSystemPath()
        project.extensions.findByType(BaseExtension::class.java)?.bootClasspath?.forEach {
            classPool.appendClassPath(it.absolutePath)
        }

        invocation.inputs.forEach { input ->
            input.directoryInputs.forEach { classPool.appendClassPath(it.file.absolutePath) }
            input.jarInputs.forEach { classPool.appendClassPath(it.file.absolutePath) }
        }
        invocation.referencedInputs.forEach { input ->
            input.directoryInputs.forEach { classPool.appendClassPath(it.file.absolutePath) }
            input.jarInputs.forEach { classPool.appendClassPath(it.file.absolutePath) }
        }

        return classPool
    }

    private fun collectClassNames(invocation: TransformInvocation): List<String> =
        invocation.inputs
            .flatMap { it.directoryInputs }
            .flatMap { listFilesRecursively(it.file).map { file -> file.relativeTo(it.file) } }
            .map { it.path }
            .filter { it.endsWith(SdkConstants.DOT_CLASS) }
            .map { pathToClassName(it) }


    private fun pathToClassName(path: String): String {
        return path.substring(0, path.length - SdkConstants.DOT_CLASS.length)
            .replace("/", ".")
            .replace("\\", ".")
    }

    private fun listFilesRecursively(dir: File): Collection<File> {
        val list = arrayListOf<File>()

        dir.listFiles().forEach { file ->
            if (file.isDirectory) {
                list.addAll(listFilesRecursively(file))
            } else if (file.isFile) {
                list.add(file)
            }
        }

        return list
    }
}