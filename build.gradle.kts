plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "9.4.1"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/"){
        name = "spigot-snapshots"
    }
    maven("https://jitpack.io"){
        name = "jitpack"
    }
    maven("https://maven.aliyun.com/repository/public/"){
        name = "Aliyun"
    }
    maven("https://maven.aliyun.com/repository/central"){
        name = "central"
    }
    maven("https://repo.alessiodp.com/releases/"){
        name = "libby"
    }
    maven("https://repo.extendedclip.com/releases/") {
        name = "placeholderapi"
    }
    maven("https://jitpack.io") {
        name = "vaultapi"
    }
}

dependencies {
    implementation("net.byteflux:libby-bukkit:1.3.0")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    compileOnly("org.spigotmc:spigot-api:1.18.2-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    implementation("fr.mrmicky:fastboard:2.1.5")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
}

kotlin {
    jvmToolchain(17)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
