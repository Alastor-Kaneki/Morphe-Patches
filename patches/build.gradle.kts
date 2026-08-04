group = "dev.alastorkaneki"

patches {
    about {
        name = "Alastor Kaneki Morphe Patches"
        description = "A shared collection of Morphe patches, including the Opera GX raw mod downloader."
        source = "https://github.com/Alastor-Kaneki/Morphe-Patches"
        author = "Alastor Kaneki"
        contact = "https://github.com/Alastor-Kaneki"
        website = "https://github.com/Alastor-Kaneki/Morphe-Patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
