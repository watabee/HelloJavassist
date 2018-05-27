package jp.watabee.logger

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class LoggerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val hasApp = project.plugins.withType(AppPlugin::class.java)
        val hasLib = project.plugins.withType(LibraryPlugin::class.java)
        if (hasApp.isEmpty() && hasLib.isEmpty()) {
            throw GradleException("'android' or 'android-library' plugin required.")
        }

        project.extensions.findByType(BaseExtension::class.java)
            ?.registerTransform(LoggerTransformer(project))
    }
}