# EMA Wallet-First Commerce Specification

Status: approved product direction; implementation contract for the test branch.
Scope: prepaid wallet, metered AI usage, permanent limited free tier. No paid subscription plans.

## 1. Commercial model

- EMA uses wallet top-ups and pay-as-you-go billing for paid AI features.
- The free tier is permanent, limited, and does not require a subscription or trial expiry.
- Paid subscriptions and subscription credit grants are not part of the intended purchase experience.
- A feature must never run as a paid operation unless its server-side price and billing unit are configured and enabled.
- The client may display estimates, but the server is authoritative for price, eligibility, reservation, settlement, and refunds.

## 2. Separate balances

Expose two independent balances to the user and administrator:

1. Purchased balance: funds added through confirmed top-up payments or explicitly recorded paid adjustments.
2. Bonus balance: promotional or goodwill credit granted by EMA. It is not cash, is not refundable, and must never be reported as collected revenue.

Keep free feature quotas separate from both wallet balances. A free quota is a usage allowance (for example, a daily number of basic chat turns), not money and not a wallet deposit.

Recommended default debit order for paid operations: bonus credit first, then purchased credit. Show both balances separately in the UI and record the source split on every reservation and settlement. A future admin setting may change debit order, but it must not silently change for existing reservations.

## 3. Free-tier policy

Free allowances are configured per feature and may be limited by:
- daily or monthly count;
- maximum input/output size;
- feature availability;
- per-user and global capacity limits.

The admin must be able to enable/disable a free allowance and set its quota. Quota reset windows use UTC internally and show the user's local reset time in the app. Free usage must not debit either wallet balance. Any usage beyond the free allowance requires a paid wallet reservation before the provider call.

## 4. Price catalog

Every paid feature has a server-owned pricing rule with:
- feature and model/provider identifier;
- billing unit (tokens, characters, seconds, request, or minutes);
- provider input/output rates where applicable;
- customer markup;
- enabled state;
- optional maximum charge per operation.

Do not enable a rule until all required provider rates, exchange rate, and billing-unit fields are valid. Never infer a price from a missing rate. Unsupported or unpriced paid media features must return a clear unavailable/not-configured response before starting provider work.

## 5. Wallet accounting invariants

- All money values are integer toman.
- Purchased and bonus balances cannot become negative.
- Every balance mutation has one immutable ledger transaction with a unique reference/idempotency key.
- Every usage reservation records how much was reserved from each balance bucket.
- Settlement refunds unused reservation amounts to the same source buckets.
- If actual cost exceeds the reservation, the server must not charge beyond the reserved amount unless an additional atomic reservation succeeds.
- Failed provider operations release the full reservation.
- Unknown/missing provider usage is not silently treated as free. Keep the reservation in a reconcilable state and flag it for admin review.
- Top-ups become purchased balance only after verified payment approval.
- Bonus grants and manual corrections require a reason and admin audit log.

## 6. User experience

Wallet screen:
- show purchased and bonus balances separately;
- show the next action to top up when purchased/bonus funds are insufficient;
- show free quota remaining per feature;
- show pending, confirmed, rejected, and refunded top-up orders;
- show a transaction history with clear labels for top-up, bonus grant, usage debit, refund, and admin correction.

Before a paid operation:
- show a server-derived estimate or maximum reservation amount;
- state that final charge is based on measured usage;
- do not start if the user cannot cover the required reservation;
- refresh the displayed balances after settlement.

## 7. Owner admin panel

Sections:
- Overview: users, actual sign-in sessions, purchased wallet liability, bonus liability, confirmed top-ups, paid usage revenue, provider cost, and estimated gross margin.
- Users: email, signup date, last real sign-in, sign-in count, account status, purchased balance, bonus balance, free quota usage, and feature usage summaries.
- Wallet: purchased/bonus balance adjustments, each with reason, actor, timestamp, and ledger reference.
- Free tier: per-feature free quotas, reset periods, limits, and enable/disable controls.
- Pricing: model/provider rates, billing unit, markup, maximum charge, and safe enable/disable validation.
- Payments: top-up orders, receipts, manual review, payment status, and confirmed amount.
- Usage analytics: feature/model counts, billed amount, provider cost, failures, refunds, and margin.

Do not store or expose private chat/media contents for usage analytics. Store metadata needed for accounting and product metrics only.

## 8. Authentication and analytics

A real sign-in event must be recorded at the authentication/session creation boundary, not on every authenticated API request. Store session start and (where available) end/expiry, and derive sign-in count from these events. API activity and feature usage are separate event types.

Disclose account, sign-in, and usage analytics in the privacy notice. Collect the minimum metadata needed for security, accounting, and service improvement.

## 9. Migration and rollout plan

1. Add purchased and bonus balance columns while preserving existing wallet balances as purchased funds.
2. Extend ledger and usage reservation records to capture source-bucket amounts.
3. Update all reserve, settle, refund, top-up, admin adjustment, and summary queries atomically.
4. Add feature quota tables and server-side quota enforcement.
5. Remove subscription purchase choices and subscription-derived access from the customer app and owner console. Retain historical subscription/payment records for audit and compatibility; do not delete financial history.
6. Add sign-in session events and owner analytics.
7. Add tests for concurrent reservations, insufficient balances, failed provider calls, settlement/refund, idempotent top-ups, and free quota reset.
8. Roll out only after migrations, backend tests, Android build, and manual end-to-end payment/usage tests pass.

## 10. Current implementation gap

The current test branch has a single `users.wallet_balance_toman` balance and a working reservation/settlement path for text chat. It also contains subscription-oriented catalog, order, admin UI, and Android wallet screens. Multimodal pricing fields exist, but this does not mean every image/video/audio feature is safely billed end-to-end.

This document records the approved target behavior; it does not claim that the migration or full wallet-first implementation has already been deployed.
