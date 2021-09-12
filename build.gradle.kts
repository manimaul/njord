import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.30"
    kotlin("kapt") version "1.5.30"
}

repositories {
    mavenCentral()
}

allprojects {

    apply(plugin = "kotlin")
    apply(plugin = "kotlin-kapt")

    repositories {
        mavenLocal()
        maven {
            url = uri("https://maven.pkg.github.com/manimaul/vial")
            credentials {
                username = "${project.findProperty("github_user")}"
                password = "${project.findProperty("github_token")}"
            }
        }
        mavenCentral()
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


    }
}

task("verifyConfig") {
    val githubUser = project.findProperty("github_user")?.toString()
    val githubToken = project.findProperty("github_token")?.toString()
    doLast {
        println("bintray_user: ${if (githubUser.isNullOrBlank()) "missing!" else githubUser}")
        println("bintray_key: ${if (githubToken.isNullOrBlank()) "missing!" else "*****"}")
        println("version: ${project.version}")
        println("group: ${project.group}")
    }
}
