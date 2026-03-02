plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    group = "com.github.irangareddy.tiptap-compose"
}
