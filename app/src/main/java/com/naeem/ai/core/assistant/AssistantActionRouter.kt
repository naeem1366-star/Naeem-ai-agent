package com.naeem.ai.core.assistant

/**
 * The only execution gateway for assistant-originated module actions.
 *
 * READ actions can run after validation.
 * WRITE actions are held in memory until the user explicitly confirms them.
 * Pending actions disappear if the process is killed, which is intentionally
 * safer than silently replaying an unconfirmed write after app restart.
 */
class AssistantActionRouter(
    private val registry: ToolRegistry
) {
    private val pendingActions = mutableMapOf<String, AssistantAction>()

    suspend fun route(action: AssistantAction): ActionRouteResult {
        val tool = registry.get(action.module)
            ?: return ActionRouteResult.Rejected(
                "ماژول " + action.module.name + " در دسترس نیست."
            )

        val safeAction = action.copy(parameters = action.parameters.toMap())

        return when (val validation = tool.validate(safeAction)) {
            ActionValidation.Valid -> {
                if (safeAction.access == ActionAccess.WRITE) {
                    synchronized(pendingActions) {
                        if (pendingActions.containsKey(safeAction.id)) {
                            return ActionRouteResult.Rejected(
                                "این درخواست قبلاً برای تأیید آماده شده است."
                            )
                        }
                        pendingActions[safeAction.id] = safeAction
                    }
                    ActionRouteResult.ConfirmationRequired(safeAction)
                } else {
                    executeSafely(tool, safeAction)
                }
            }

            is ActionValidation.NeedsClarification ->
                ActionRouteResult.ClarificationRequired(validation.question)

            is ActionValidation.Invalid ->
                ActionRouteResult.Rejected(validation.reason)
        }
    }

    /**
     * A confirmation id can be consumed only once. The UI must call this
     * method only from the user's explicit confirmation action.
     */
    suspend fun confirm(actionId: String): ActionRouteResult {
        val action = synchronized(pendingActions) {
            pendingActions.remove(actionId)
        } ?: return ActionRouteResult.Rejected(
            "درخواست تأییدشده‌ای با این شناسه وجود ندارد یا قبلاً اجرا شده است."
        )

        val tool = registry.get(action.module)
            ?: return ActionRouteResult.Rejected("ماژول موردنظر دیگر در دسترس نیست.")

        return when (val validation = tool.validate(action)) {
            ActionValidation.Valid -> executeSafely(tool, action)
            is ActionValidation.NeedsClarification ->
                ActionRouteResult.ClarificationRequired(validation.question)
            is ActionValidation.Invalid ->
                ActionRouteResult.Rejected(validation.reason)
        }
    }

    fun cancel(actionId: String): Boolean =
        synchronized(pendingActions) {
            pendingActions.remove(actionId) != null
        }

    fun pendingActionIds(): Set<String> =
        synchronized(pendingActions) { pendingActions.keys.toSet() }

    private suspend fun executeSafely(
        tool: AssistantTool,
        action: AssistantAction
    ): ActionRouteResult = try {
        ActionRouteResult.Completed(tool.execute(action))
    } catch (error: Exception) {
        ActionRouteResult.Failed(
            error.message?.takeIf { it.isNotBlank() }
                ?: "اجرای عملیات با خطای نامشخص روبه‌رو شد."
        )
    }
}
