import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.71"
    kotlin("kapt") version "1.3.71"
}

repositories {
    jcenter()
}

val rxJavaVersion = "2.2.12"
val daggerVersion = "2.24"
val slf4jVersion = "1.7.25"

allprojects {

    apply(plugin = "kotlin")
    apply(plugin = "kotlin-kapt")

    repositories {
        jcenter()
    }

    //https://kotlinlang.org/docs/reference/using-gradle.html#compiler-options
    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.apiVersion = "1.3"
    }

    dependencies {
        /* Kotlin */
        implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        testImplementation("org.jetbrains.kotlin:kotlin-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

        /* RxJava */
        implementation("io.reactivex.rxjava2:rxjava:${rxJavaVersion}")

        /* Logging */
        implementation("org.slf4j:slf4j-api:${slf4jVersion}")

        /* Dagger 2*/
        implementation("com.google.dagger:dagger:${daggerVersion}")
        kapt("com.google.dagger:dagger-compiler:${daggerVersion}")
        kaptTest("com.google.dagger:dagger-compiler:${daggerVersion}")
    }
}

task("verifyBintrayConfig") {
    val bintray_user: String? by project
    val bintray_key: String? by project
    doLast {
        println("bintray_user: ${if (bintray_user.isNullOrBlank()) "missing!" else bintray_user}")
        println("bintray_key: ${if (bintray_key.isNullOrBlank()) "missing!" else "*****"}")
        println("version: ${project.version}")
        println("group: ${project.group}")
    }
}
