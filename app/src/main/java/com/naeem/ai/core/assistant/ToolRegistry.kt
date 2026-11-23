package com.naeem.ai.core.assistant

/**
 * Immutable registry of tools available to the assistant.
 * Duplicate module registrations fail fast during app composition.
 */
class ToolRegistry(tools: Collection<AssistantTool>) {
    private val toolsByModule: Map<AssistantModule, AssistantTool>

    init {
        val grouped = tools.groupBy { it.module }
        require(grouped.values.none { it.size > 1 }) {
            "Only one tool may be registered for each assistant module."
        }
        toolsByModule = grouped.mapValues { (_, value) -> value.single() }
    }

    fun get(module: AssistantModule): AssistantTool? = toolsByModule[module]

    fun availableModules(): Set<AssistantModule> = toolsByModule.keys
}
