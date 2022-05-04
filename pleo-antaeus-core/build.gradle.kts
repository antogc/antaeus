plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-data"))
    api(project(":pleo-antaeus-models"))

    implementation("com.michael-bull.kotlin-retry:kotlin-retry:1.0.9")
    testImplementation("io.kotest:kotest-assertions-core:5.3.0")
}