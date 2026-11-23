# Naeem AI — Assistant and Module Integration

## Product rule

Every feature remains independently usable from its own screen. The assistant is an additional access path, not a replacement for module interfaces.

The first integration scope includes Finance, Sports, Travel, and Memory. Each module owns its validation rules and repository operations. The assistant can only call explicitly registered tools.

## Confirmed interaction policy

- **Writes: confirm.** Every create, update, or delete action requires a clear preview and an explicit user confirmation.
- **Ambiguity: ask.** Missing or uncertain values must be clarified; the system must not silently guess.
- **Scope: all.** Finance, Sports, Travel, and Memory are included in the first integration architecture.

Read-only requests can execute after validation. A write request is prepared first, shown to the user, and executed only after confirmation.

## Runtime flow

1. The conversation layer receives text or a speech transcript.
2. Intent resolution produces a normalized AssistantAction.
3. AssistantActionRouter finds the registered module tool.
4. The tool validates required fields and business rules.
5. Read operations execute after validation.
6. Write operations return ConfirmationRequired; no data is changed yet.
7. The UI displays a preview and asks the user to confirm or cancel.
8. On confirmation, the router revalidates and executes the operation once.
9. The module repository persists the result and the UI refreshes from the same source of truth.

## Current code foundation

Package: com.naeem.ai.core.assistant

- AssistantAction.kt: module identifiers, access mode, normalized action, validation and route result types.
- AssistantTool.kt: contract implemented by each module adapter.
- ToolRegistry.kt: explicit registry with duplicate-registration protection.
- AssistantActionRouter.kt: read routing and confirmation-gated write execution.

The router intentionally keeps unconfirmed writes in memory only. If the app process ends, pending actions are discarded rather than replayed. Tool implementations must use the same repositories as their standalone module screens.

## Module adapter responsibilities

| Adapter | Initial read operations | Initial write operations |
|---|---|---|
| Finance | Monthly totals, recent transactions, category totals | Add income, add expense |
| Sports | Workout history, monthly duration, distance and session count | Add workout |
| Travel | Trip list, trip expenses, participant balances | Add trip expense |
| Memory | Search and list saved notes | Add memory note |

Delete and edit operations should be added only after create/read flows are stable. They must always require confirmation.

## Data and safety requirements

- The assistant must not receive direct access to SharedPreferences, Room DAOs, or arbitrary database queries.
- Each tool exposes named operations and validates all parameters.
- Money is represented as integer Toman values in the existing finance feature; no floating-point calculations.
- Ambiguous amount units, especially Toman versus Rial or colloquial shorthand, require clarification.
- Action IDs must be unique. Repository writes should also enforce idempotency to prevent duplicate records after retries.
- Tool output should contain only the information needed to answer the user.
- The standalone module remains fully functional if AI connectivity is unavailable.

## Delivery sequence

### Milestone A — Core contracts
Add action models, tool contract, registry, and confirmation-aware router. Build and review this foundation before connecting any live module.

### Milestone B — Read adapters
Connect Finance, Sports, Travel, and Memory read operations to their existing sources of truth. Verify that assistant answers match each module's own dashboard.

### Milestone C — Confirmed writes
Add create operations with a reusable confirmation preview. Test confirm, cancel, missing fields, duplicate requests, and process interruption.

### Milestone D — Conversation integration
Connect intent resolution and action cards to the existing chat UI. Keep normal conversational replies and all standalone module entry points unchanged.

### Milestone E — Backup and multi-conversation readiness
Move durable records toward the shared Room data layer, migrate legacy local records safely, and add versioned export/import before expanding cross-module analytics.

## Not yet connected

The core contracts are a foundation, not a claim that module actions are already live. The module adapters, intent extraction, confirmation UI, repository migrations, and end-to-end tests remain subsequent implementation work.
