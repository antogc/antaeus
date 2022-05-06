plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-data"))
    api(project(":pleo-antaeus-models"))

    implementation("dev.inmo:krontab:0.7.2")
    implementation("com.michael-bull.kotlin-retry:kotlin-retry:1.0.9")
}