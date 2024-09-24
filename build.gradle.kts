plugins {
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") apply false
    kotlin("plugin.compose") apply false
}

tasks.register<Delete>("clean") {
    delete = setOf(layout.buildDirectory.get())
}