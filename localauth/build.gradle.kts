import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("com.vanniktech.maven.publish")
}

/**
 * Run 'publishToMavenCentral' to release the library Maven Central. Make sure the following variables
 * are correctly set in your 'gradle.properties' file:
 * mavenCentralUsername, mavenCentralPassword, signing.keyId, signing.password, signing.secretKeyRingFile
 *
 * For testing, run 'publishToMavenLocal' to publish to your local maven repository. Add 'mavenLocal()'
 * as a repository (settings.gradle.kts > dependencyResolutionManagement.repositories). Then make sure
 * the library is working correctly.
 */
mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates("com.negusoft.localauth", "localauth", "0.8.0")

    pom {
        name.set("LocalAuth")
        description.set("Protect critical user data by defining authentication methods to access it, such as a password or biometric verification.")
        inceptionYear.set("2024")
        url.set("https://github.com/username/mylibrary/")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("blurkidi")
                name.set("Borja Lopez Urkidi (Negusoft)")
                url.set("https://github.com/negusoft/")
            }
        }
        scm {
            url.set("https://github.com/negusoft/localauth-android")
            connection.set("scm:git:git://github.com/negusoft/localauth-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/negusoft/localauth-android.git")
        }
    }
}

android {
    namespace = "com.negusoft.localauth"
    compileSdk = 34
    version = "0.8"

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlin.serialization)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}