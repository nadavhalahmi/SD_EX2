val externalLibraryVersion: String? by extra
val guiceVersion: String? by extra
val kotlinGuiceVersion: String? by extra
val mockkVersion: String? by extra

dependencies {
    implementation("il.ac.technion.cs.softwaredesign", "primitive-storage-layer", externalLibraryVersion)
    implementation("com.google.inject", "guice", guiceVersion)
    implementation("dev.misfitlabs.kotlinguice4", "kotlin-guice", kotlinGuiceVersion)
    testImplementation("io.mockk", "mockk", mockkVersion)
}