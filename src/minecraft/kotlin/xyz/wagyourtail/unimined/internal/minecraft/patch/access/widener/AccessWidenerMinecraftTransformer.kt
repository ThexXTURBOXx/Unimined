package xyz.wagyourtail.unimined.internal.minecraft.patch.access.widener

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.minecraft.patch.ataw.AccessConvert
import xyz.wagyourtail.unimined.api.minecraft.patch.ataw.AccessWidenerPatcher
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.mapping.aw.AccessWidenerApplier
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.api.minecraft.MinecraftJar
import xyz.wagyourtail.unimined.internal.minecraft.patch.access.AccessConvertImpl
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.getShortSha1
import java.io.File
import kotlin.io.path.exists


open class AccessWidenerMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider,
    providerName: String = "accessWidener",
) : AbstractMinecraftTransformer(
    project,
    provider,
    providerName
), AccessWidenerPatcher, AccessConvert by AccessConvertImpl(project, provider) {

    override var accessWidener: File? by FinalizeOnRead(null)

    override fun afterRemap(baseMinecraft: MinecraftJar): MinecraftJar {
        if (accessWidener == null) return baseMinecraft
        return applyAW(baseMinecraft)
    }

    private fun applyAW(baseMinecraft: MinecraftJar): MinecraftJar {
        return if (accessWidener != null) {
            val output = MinecraftJar(
                baseMinecraft,
                awOrAt = "aw+${accessWidener!!.toPath().getShortSha1()}"
            )
            if (!output.path.exists() || project.unimined.forceReload) {
                if (AccessWidenerApplier.transform(
                        accessWidener!!.toPath(),
                        AccessWidenerApplier.nsName(provider.mappings, baseMinecraft.mappingNamespace),
                        baseMinecraft.path,
                        output.path,
                        false,
                        project.logger
                    )
                ) {
                    output
                } else {
                    baseMinecraft
                }
            } else {
                output
            }
        } else baseMinecraft
    }

}