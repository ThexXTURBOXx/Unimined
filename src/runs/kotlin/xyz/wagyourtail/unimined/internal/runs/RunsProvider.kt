package xyz.wagyourtail.unimined.internal.runs

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.runs.RunsConfig
import xyz.wagyourtail.unimined.api.runs.auth.AuthConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.runs.auth.AuthProvider
import xyz.wagyourtail.unimined.util.*

class RunsProvider(val project: Project, val minecraft: MinecraftConfig): RunsConfig() {
    private var freeze = false
    private var runTasks: Map<String, TaskProvider<RunConfig>> by FinalizeOnRead(MustSet())

    override val auth = AuthProvider(project)

    private val preLaunchTasks = defaultedMapOf<String, MutableList<Any>> { mutableListOf() }
    private val preLaunch = mutableMapOf<String, RunConfig.() -> Unit>()
    private val actions = mutableMapOf<String, RunConfig.() -> Unit>()
    private var all: RunConfig.() -> Unit = {}

    fun freezeCheck() {
        if (freeze) throw IllegalStateException("Cannot change run configs after apply has been called.")
    }

    override fun preLaunch(config: String, action: RunConfig.() -> Unit) {
        freezeCheck()
        val old = preLaunch[config]
        preLaunch[config] = {
            old?.invoke(this)
            action.invoke(this)
        }
    }

    override fun preLaunch(config: String, action: TaskProvider<Task>) {
        freezeCheck()
        preLaunchTasks[config].add(action)
    }

    override fun preLaunch(config: String, action: Task) {
        freezeCheck()
        preLaunchTasks[config].add(action)
    }

    override fun preLaunch(config: String, action: String) {
        freezeCheck()
        preLaunchTasks[config].add(action)
    }

    override fun config(config: String, action: RunConfig.() -> Unit) {
        freezeCheck()
        val old = actions[config]
        actions[config] = {
            old?.invoke(this)
            action.invoke(this)
        }
    }

    override fun configFirst(config: String, action: RunConfig.() -> Unit) {
        freezeCheck()
        val old = actions[config]
        actions[config] = {
            action.invoke(this)
            old?.invoke(this)
        }
    }

    override fun auth(action: AuthConfig.() -> Unit) {
        freezeCheck()
        auth.apply(action)
    }

    override fun all(action: RunConfig.() -> Unit) {
        freezeCheck()
        val old = all
        all = {
            old.invoke(this)
            action.invoke(this)
        }
    }

    val genIntellijRunsTask = project.tasks.register("genIntellijRuns".withSourceSet(minecraft.sourceSet)) {
        if (minecraft.sourceSet == project.sourceSets.getByName("main")) {
            it.group = "unimined_runs"
            it.dependsOn(*project.unimined.minecrafts.keys.filter { it != minecraft.sourceSet }.map { "genIntellijRuns".withSourceSet(it) }.mapNotNull { project.tasks.findByName(it) }.toTypedArray())
        } else {
            it.group = "unimined_internal"
        }
        it.doLast {
            for (value in runTasks.values) {
                value.get().createIdeaRunConfig()
            }
        }
    }

    fun apply() {
        freeze = true
        if (off) return
        if ((preLaunch.keys - actions.keys).isNotEmpty()) {
            throw IllegalStateException("You have preLaunch for run configs that don't exist: ${preLaunch.keys - actions.keys}")
        }
        runTasks = actions.mapValues { action ->
            val preRun: TaskProvider<Task> = project.tasks.register("preRun${action.key.capitalized()}".withSourceSet(minecraft.sourceSet))

            val task: TaskProvider<RunConfig> = project.tasks.register("run${action.key.capitalized()}".withSourceSet(minecraft.sourceSet), RunConfig::class.java, minecraft.sourceSet, preRun)
            task.configure {
                it.apply(all)
                it.apply(action.value)
            }

            preRun.configure {
                it.group = "unimined_internal"
                it.dependsOn(*preLaunchTasks[action.key].toTypedArray())
                it.doLast {
                    preLaunch[action.key]?.invoke(task.get())
                }
            }


            task
        }
        //TODO: vscode/eclipse support

        if (System.getProperty("idea.sync.active", "false").lowercase() == "true") {
            project.afterEvaluate {
                for (value in runTasks.values) {
                    value.get().createIdeaRunConfig()
                }
            }
        }
    }
}