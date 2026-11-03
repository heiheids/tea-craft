# NeoForge 26.3 kiln batch ignition regression

The kiln screen now exposes the existing server menu action, `BUTTON_IGNITE`.
For a kiln that already contains fuel, queue the desired blanks and press
**Start batch**. Adding fuel after the blanks retains the existing automatic
ignition behavior. Starting at the first `addBlank` would lock out the remaining
five orders, so batch assembly remains editable until ignition.

## Manual verification

Use the NeoForge 26.3 project and a fresh test world. Place a kiln next to a
potter wheel. Keep output slots empty. The expected duration is 24000 server
ticks; the display maps this to `24:00` in game hours and minutes.

| Scenario | Steps | Expected result |
| --- | --- | --- |
| Fuel first | Put 64 coal into an empty kiln, then queue six blanks | No countdown or coal deduction until Start batch; all six orders remain editable |
| Start full batch | Press Start batch | Server starts all six, coal becomes 56, the start button disables |
| Duplicate start | Reopen the screen or send the start action again while burning | Remaining time continues; no restart or extra coal charge |
| Full cycle | Advance 24000 server ticks, optionally with `/tick sprint 24000` | Six exact products, no blanks, coal still 56 |
| Leftover fuel | Remove the products and queue six new blanks without changing the remaining coal | Start batch is available; starting consumes eight coal, leaving 48 |
| Fuel last | Queue blanks into an unfueled kiln, then add eight coal | Existing automatic server ignition still works |
| Missing input | Try with no blanks or fewer than eight coal | No ignition or fuel deduction |
| Client unload | Keep the server chunk running with `/forceload`, leave client view distance and return | Menu displays current server progress; no new full countdown or extra fuel deduction |
| Save and restart | Save, exit the JVM during a burn, and reopen the same world | Remaining progress, six blanks and fuel persist; completion produces six items |
| Inventory synchronization | Open/reopen a pre-fueled, not-yet-started kiln | Client slot initialization does not ignite, consume coal or show a countdown |

In a diagnostic client, give the client block entity a stale positive countdown
while the server menu reports zero. The screen must show the idle state and
allow starting the batch. Menu zero is authoritative; a client-local timer must
not override it.

## Executed verification environment

The corresponding maintenance checkout was built on Minecraft 26.3,
NeoForge 26.3.0.83 and Java 25. Its separate GameTest source set covers server
menu ignition, six mixed ceramic outputs, fuel ordering, a second batch with
leftover fuel, duplicate requests, insufficient fuel, persistence serialization,
and native inventory initialization without a server world. It also retains
the existing tea-bush interaction tests. Those test infrastructure changes are
not part of this upstream patch.

The patch is limited to NeoForge 26.3. The other loader/version projects have
not been validated by this change. Existing Shift-transfer slot indexing and
blocked-output completion behavior are separate issues.

The upstream checkout also completed `./gradlew assemble` with its unchanged
NeoForge 26.3.0.67-beta pin and Java 25, and with a command-line override to
26.3.0.83. Runtime testing used 26.3.0.83. A ten-mod client completed two mixed
six-item batches through native keyboard activation of the visible button,
with 64 -> 56 -> 48 coal. Client unloading and return, a real JVM restart
during the first burn, and another restart after both batches retained the
correct server state. Full cycles were advanced with vanilla tick sprint.
