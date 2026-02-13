plugins {
    id("com.android.library")
}

android {
    namespace = "com.example.stripeandroidsource"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

configurations {
    create("copyDependencies")
}

dependencies {
    implementation("com.stripe:stripeterminal-taptopay:5.1.1")
    implementation("com.stripe:stripeterminal-core:5.1.1")

    "copyDependencies"("com.stripe:stripeterminal-taptopay:5.1.1")
    "copyDependencies"("com.stripe:stripeterminal-core:5.1.1")
}

project.afterEvaluate {
    tasks.register<Copy>("copyDeps") {
        from(configurations["copyDependencies"])
        into("${projectDir}/build/outputs/deps")
    }
    tasks.named("preBuild") { finalizedBy("copyDeps") }
}
