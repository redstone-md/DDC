# Architectural Decision Record

## Title: 0003-gm-networking-sync

## Status
Approved

## Context
The Game Master (GM) role is highly interactive and asymmetrical. The GM needs to possess mobs, spawn complex encounters, trigger localized narration titles, and play audio files dynamically. This creates two distinct technical challenges:
1. **Network Overhead**: Syncing possession controls, real-time client inputs (when a GM controls a monster), and multi-block grid outlines could flood the network buffer, causing latency for players.
2. **Security**: GMs have administrative control over the game world. If a malicious client simulates GM packet payloads (e.g. sending a "possess wither" or "force narrate" packet), they could easily grief servers.

## Decision
We will enforce a strict **Server-Authoritative Validation Protocol** and utilize **Compressed Networking Payloads** for all GM commands.
- The server maintains the master state of GMs. When a client joins, the server validates if the player is a GM (OP level >= 2 or specified in the config). Only then is the GM capability attached.
- All incoming C2S packets from GMs (such as possession commands or narrations) are verified on the server-side packet listener. If the sender is not a registered GM, the packet is discarded and the player is flagged (and optionally kicked).
- For possession, instead of raw input mirroring, we use standard client control packets routed through a Server-side Proxy Controller. The server translates the GM's standard movement keys (W, A, S, D, space, mouse click) into mob AI pathfinding modifications, keeping network data tiny.

## Consequences
- **Pros**:
  - Secure: Prevents hacked clients from injecting administrative packets.
  - Low latency: Network data for possession is minimal, keeping server TPS healthy.
- **Cons**:
  - Possessing a mob may feel slightly less responsive on high-ping connections because the server acts as the validator and pathfinder (rather than full client-side client prediction).
