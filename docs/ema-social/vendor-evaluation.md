# EMA Social infrastructure evaluation — 2026-11-28

## Decision status

No messaging or calling vendor is wired into the Android app yet. The initial social profile/discovery/matching APIs are kept independent of any chat or RTC vendor. This avoids locking the product to an untested service.

## OpenIM

### Verified facts

- OpenIM Server is self-hostable and the official deployment guide supports Docker Compose, source deployment, and cluster deployment.
- The server repository is Apache-2.0.
- The official Android SDK repository is AGPL-3.0, with a commercial license alternative. This is a material legal distinction: the server's Apache license does not make the Android SDK Apache-licensed.
- The current official Android SDK setup requires an accessible server HTTP endpoint and WebSocket endpoint, plus user credentials/token; physical devices must reach both.
- The official Docker deployment brings several operational dependencies (including MongoDB, Redis, Kafka, Etcd and MinIO in the documented deployment). This is a meaningful operational footprint for a first deployment.

### EMA recommendation

Do not add the official OpenIM Android SDK to EMA until the AGPL implications are reviewed or a commercial SDK license is obtained. OpenIM Server itself remains a candidate under Apache-2.0. Alternatives are:
1. obtain a commercial license for the Android SDK, or
2. evaluate implementing a thin EMA-owned Android adapter against the documented server APIs/WebSocket protocol, after a technical proof of concept and compatibility review.

Before choosing OpenIM, test stable tagged releases only, deployment footprint, message sync, offline delivery, push notifications, file uploads, reconnect/background behavior, and network quality from Iranian mobile operators.

## LiveKit

### Verified facts

- LiveKit Server is open source under Apache-2.0.
- LiveKit provides an Android SDK under Apache-2.0.
- LiveKit supports self-hosting on VMs or Kubernetes and documents production deployment, TLS, load balancing, TURN, and firewall configuration.
- LiveKit Cloud has a free Build plan, a Ship plan starting at USD 50/month, and a Scale plan starting at USD 500/month; those are managed-cloud prices, not the cost of self-hosting.
- Self-hosting moves infrastructure, monitoring, bandwidth, TURN capacity, and operational responsibility to EMA.

### EMA recommendation

LiveKit is the stronger current candidate for voice/video if the team can operate a media server in Iran. Start with a single-region self-hosted proof of concept, use short-lived room tokens issued only by the authenticated EMA backend, and test UDP/TCP/TLS fallback and TURN relay from Irancell and other Iranian mobile networks. Never place the LiveKit API secret in the APK.

## Hosting and geography

The current EMA Supabase project is hosted in eu-west-1, not Iran. Therefore, the present Supabase database and smooth-service are not an Iranian-hosted deployment.

A real Iran-hosted production architecture needs a separately provisioned Iranian Linux environment with:
- Docker/Compose initially, with a later path to orchestration if load requires it;
- PostgreSQL with tested backups and restore;
- Redis for transient state/queues if justified by measured workload;
- S3-compatible object storage with private buckets and signed URLs;
- HTTPS domains and WebSocket-capable reverse proxy;
- firewall policy, rate limiting, monitoring, alerting, and off-host backups.

No Iranian provider is selected in this branch because actual price, network reachability, data-center location, DDoS protection, backup terms, and payment method must be checked against a concrete provider quote and real device tests. Do not silently treat the existing European Supabase project as satisfying the Iran-hosting requirement.

## Global emergency switch — required behavior

The current first implementation persists social_network.enabled in PostgreSQL and checks it server-side on every EMA Social API route. The switch is only writable by a superadmin, and every change is audited.

For the full system, the same control must also be enforced at the service edges:
- do not issue new OpenIM access tokens or LiveKit room tokens while disabled;
- terminate/revoke existing OpenIM sessions and close active LiveKit rooms when the switch is turned OFF;
- invalidate cached enabled-state quickly and fail closed if the control service is unreachable;
- keep only the status and superadmin recovery endpoints available while OFF;
- retain user data and make reactivation reversible.

The current branch has no OpenIM or LiveKit deployment, so there are no live messaging/call sessions to terminate yet. The active-session termination hooks must be implemented alongside those services before claiming the emergency switch shuts down already-established calls or WebSocket sessions.

## Decision matrix

| Area | Candidate | Current assessment |
|---|---|---|
| Social profile/Discover/Match | EMA-owned API + PostgreSQL | Implemented as initial foundation |
| Messaging | OpenIM Server | Candidate; SDK license and Iran network test required |
| Android messaging SDK | OpenIM Android SDK | Hold pending AGPL/commercial license decision |
| Voice/video | Self-hosted LiveKit | Preferred candidate; Iran network/ TURN benchmark required |
| Database | PostgreSQL | Existing Supabase is EU; Iran-hosted production not provisioned |
| Semantic matching | pgvector | Not enabled in current DB; evaluate after profile data and consent model are stable |
| Cache/queues | Redis | Add only with measured need and an operator plan |
| Object storage | S3-compatible / MinIO | Candidate; private bucket and signed URL design required |

## Sources

- OpenIM Android prerequisites: https://docs.openim.io/sdk/android/getting-started/before-you-start
- OpenIM Docker Compose deployment: https://docs.openim.io/docs/guides/deployment/docker-compose
- OpenIM Server license: https://github.com/openimsdk/open-im-server
- OpenIM Android SDK license: https://github.com/openimsdk/open-im-sdk-android
- LiveKit self-hosting: https://docs.livekit.io/transport/self-hosting/
- LiveKit server license: https://github.com/livekit/livekit
- LiveKit pricing: https://livekit.com/pricing
