package com.naeem.ai.core.assistant

/**
 * A normalized request produced by the assistant intent layer.
 * The model may propose an action, but it never executes it directly.
 * All writes pass through AssistantActionRouter and user confirmation.
 */
enum class AssistantModule { FINANCE, SPORTS, HEALTH, TRAVEL, MEMORY, JOURNAL }

enum class ActionAccess { READ, WRITE }

data class AssistantAction(
    val id: String,
    val module: AssistantModule,
    val operation: String,
    val access: ActionAccess,
    val parameters: Map<String, String>,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Action id must not be blank." }
        require(operation.isNotBlank()) { "Operation must not be blank." }
    }
}

sealed interface ActionValidation {
    data object Valid : ActionValidation
    data class NeedsClarification(val question: String) : ActionValidation
    data class Invalid(val reason: String) : ActionValidation
}

data class AssistantToolResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, String> = emptyMap()
)

sealed interface ActionRouteResult {
    data class Completed(val result: AssistantToolResult) : ActionRouteResult
    data class ConfirmationRequired(val action: AssistantAction) : ActionRouteResult
    data class ClarificationRequired(val question: String) : ActionRouteResult
    data class Rejected(val reason: String) : ActionRouteResult
    data class Failed(val reason: String) : ActionRouteResult
}
