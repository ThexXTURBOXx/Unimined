package xyz.wagyourtail.unimined.internal.minecraft.patch.fabric

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.api.minecraft.MinecraftJar
import xyz.wagyourtail.unimined.util.SemVerUtils
import java.io.InputStreamReader
import java.nio.file.Files

abstract class FabricMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider
): FabricLikeMinecraftTransformer(
    project,
    provider,
    "fabric",
    "fabric.mod.json",
    "accessWidener"
) {

    override val ENVIRONMENT: String = "Lnet/fabricmc/api/Environment;"
    override val ENV_TYPE: String = "Lnet/fabricmc/api/EnvType;"

    override fun addMavens() {
        project.unimined.fabricMaven()
    }

    override fun merge(clientjar: MinecraftJar, serverjar: MinecraftJar): MinecraftJar {
        if (canCombine) {
            return super.merge(clientjar, serverjar)
        } else if (this is BabricMinecraftTransformer || SemVerUtils.matches(fabricDep.version!!, ">=0.16.0")) {
            val INTERMEDIARY = provider.mappings.getNamespace("intermediary")
            val CLIENT = if (this is BabricMinecraftTransformer) {
                provider.mappings.findNamespace("clientOfficial") ?: provider.mappings.getNamespace("client")
            } else {
                provider.mappings.getNamespace("clientOfficial")
            }
            val SERVER = if (this is BabricMinecraftTransformer) {
                provider.mappings.findNamespace("serverOfficial") ?: provider.mappings.getNamespace("server")
            } else {
                provider.mappings.getNamespace("serverOfficial")
            }
            val clientJarFixed = MinecraftJar(
                clientjar.parentPath,
                clientjar.name,
                clientjar.envType,
                clientjar.version,
                clientjar.patches,
                CLIENT,
                CLIENT,
                clientjar.awOrAt,
                clientjar.extension,
                clientjar.path
            )
            val serverJarFixed = MinecraftJar(
                serverjar.parentPath,
                serverjar.name,
                serverjar.envType,
                serverjar.version,
                serverjar.patches,
                SERVER,
                SERVER,
                serverjar.awOrAt,
                serverjar.extension,
                serverjar.path
            )
            val intermediaryClientJar = provider.minecraftRemapper.provide(clientJarFixed, INTERMEDIARY, CLIENT)
            val intermediaryServerJar = provider.minecraftRemapper.provide(serverJarFixed, INTERMEDIARY, SERVER)
            return super.merge(intermediaryClientJar, intermediaryServerJar, true)
        }
        throw UnsupportedOperationException("Merging is not supported for this version")
    }


    override fun addIncludeToModJson(json: JsonObject, dep: Dependency, path: String) {
        var jars = json.get("jars")?.asJsonArray
        if (jars == null) {
            jars = JsonArray()
            json.add("jars", jars)
        }
        jars.add(JsonObject().apply {
            addProperty("file", path)
        })
    }

    override fun applyExtraLaunches() {
        super.applyExtraLaunches()
        if (provider.side == EnvType.DATAGEN) {
            TODO("DATAGEN not supported yet")
        }
    }

    override fun applyClientRunTransform(config: RunConfig) {
        super.applyClientRunTransform(config)
        config.jvmArgs(
            "-Dfabric.development=true",
            "-Dfabric.remapClasspathFile=${intermediaryClasspath}",
            "-Dfabric.classPathGroups=${groups}"
        )
    }

    override fun applyServerRunTransform(config: RunConfig) {
        super.applyServerRunTransform(config)
        config.jvmArgs(
            "-Dfabric.development=true",
            "-Dfabric.remapClasspathFile=${intermediaryClasspath}",
            "-Dfabric.classPathGroups=${groups}"
        )
    }

    override fun collectInterfaceInjections(baseMinecraft: MinecraftJar, injections: HashMap<String, List<String>>) {
        val modJsonPath = this.getModJsonPath()

        if (modJsonPath != null && modJsonPath.exists()) {
            val json = JsonParser.parseReader(InputStreamReader(Files.newInputStream(modJsonPath.toPath()))).asJsonObject

            val custom = json.getAsJsonObject("custom")

            if (custom != null) {
                val interfaces = custom.getAsJsonObject("loom:injected_interfaces")

                if (interfaces != null) {
                    collectInterfaceInjections(baseMinecraft, injections, interfaces)
                }
            }
        }
    }
}