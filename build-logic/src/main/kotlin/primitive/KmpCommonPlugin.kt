package primitive

import me.matsumo.grabee.library
import me.matsumo.grabee.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpCommonPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
            }

            kotlin {
                sourceSets.all {
                    languageSettings.enableLanguageFeature("ExplicitBackingFields")
                }

                sourceSets.commonMain.dependencies {
                    val kotlinBom = libs.library("kotlin-bom")
                    implementation(project.dependencies.platform(kotlinBom))
                }
            }
        }
    }
}

fun Project.kotlin(action: KotlinMultiplatformExtension.() -> Unit) {
    extensions.configure(action)
}
