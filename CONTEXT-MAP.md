# Context Map

## Contexts

- [Agent Office](./CONTEXT.md) — the terms that the mod and the bridge share
- [Mod](./src/CONTEXT.md) — the Fabric mod: agent mobs, terminal screen, browser displays
- [Bridge](./bridge/CONTEXT.md) — the local Python process between the mod and Herdr

## Relationships

- **Mod → Bridge**: The mod sends office requests and input events on the bridge socket. The bridge returns sessions and frames.
- **Bridge → Herdr**: The bridge uses Herdr RPC to list and create, and the Herdr protocol to attach. Herdr is external and is not part of this repository.
- **Mod → MCEF**: The mod uses MCEF for each browser surface. The bridge has no part in the browser features.
- **Agent Office ↔ Mod, Bridge**: The two areas use the shared terms unchanged. Each area adds only its own terms.
