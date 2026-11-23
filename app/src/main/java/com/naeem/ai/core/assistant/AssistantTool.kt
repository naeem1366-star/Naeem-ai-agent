package com.naeem.ai.core.assistant

/**
 * A module exposes a small, explicit set of operations to the assistant.
 * Implementations must use the same repositories as their standalone screens.
 */
interface AssistantTool {
    val module: AssistantModule

    suspend fun validate(action: AssistantAction): ActionValidation

    suspend fun execute(action: AssistantAction): AssistantToolResult
}
