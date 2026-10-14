Use Cavekit 3 with Codex adversarial review, falling back to the Cavekit inspector agent when Codex is unavailable, to produce the follow-up defect chart for DiamondBank-OG after the maintainer's fix pass.

## Objective

`DEFECT-CHART.md` in the repository root describes 22 defects verified at commit `0f2c29acdec4160f3db0fb477edbe38115063e6d`. The maintainer has since committed fixes. Produce two files:

1. A new `DEFECT-CHART.md` containing only the defects that still exist at the current commit, plus any defect the fixes introduced, in the same format as the current file.
2. `DEFECT-CHART-DELTA.md`, one line per row of the old chart, stating `fixed`, `partially fixed`, `unfixed` or `regressed`, with the commit and lines that changed it.

Every claim in both files is settled by reading the source at the current commit. The old chart is a set of hypotheses, not facts: a row is carried forward only if its wrong outcome still reproduces from the current code, and a row is marked fixed only if the causing lines no longer produce it.

## Inputs

* `DEFECT-CHART.md` at the start of the run. Read it first. Do not edit it in place; write the new chart to a scratch file and replace it at the end.
* `git log 0f2c29a..HEAD` and `git diff 0f2c29a HEAD`. Every file the diff touches is in scope for a fresh audit, not only the lines the old chart named.
* Dependency source, read rather than recalled: the Purpur fork at `~/eclipse-workspace/Purpur` (`Purpur-Server/src/main/java`, `Purpur-API/src/main/java`; CraftBukkit under `org/bukkit/craftbukkit/`, Paper DataConverter under `ca/spottedleaf/dataconverter/`), `libs/Utilities-OG/src/main/java`, and the jasync 2.2.4, kotlinx-coroutines 1.10.2 and Lettuce 7.5.1 sources jars in `~/.gradle/caches/modules-2/files-2.1/` (unzip to the scratchpad). A `postgres:16-alpine` podman image is available for validating SQL; use it for any query the fixes changed.

## Scope

The six surfaces of the original audit, unchanged:

1. Public API offline-player compatibility (`api/DiamondBankAPIKotlin` as K, `api/DiamondBankAPIJava` as J)
2. Public API general correctness
3. Commands (every executor registered in `DiamondBankOG.onEnable`, `util/CommonCommandInterlude`)
4. Event listeners and automatic features (`Events.kt`, `autocompress/AutoCompress`, `autodeposit/AutoDeposit`)
5. Startup, shutdown, reload, configuration, persistence setup (`onEnable`, `onDisable`, `DiamondBankReload`, `config`, `config.yml`, `plugin.yml`, `Redis.kt`, `PostgreSQL.create`, `CachingBalanceManager.create` and `shutdown`, migrations, service registration)
6. Shared internals (everything the first five reach)

Out of scope: tests under `src/test` (read them for intent only), external API consumers, build scripts.

## Contracts

A finding may say the code violates a contract only when the contract comes from: `api/README.md`; KDoc and `@Throws` on public API methods; K versus J parity; the root `README.md` command usage lines and permission list; `plugin.yml` usage and description strings; `config.yml` keys, comments and the `^{ENV}` placeholder syntax; Bukkit, Paper and Purpur runtime guards as found in the dependency source, only where a violation produces a demonstrated wrong outcome; the Guarantees below.

## Maintainer Requirements

Guarantees are contracts. Intent items are never contracts and never remediation targets.

1. Guarantee. Any database error may disable the economy. Do not recommend changing that; distinguish a concrete code defect from the intended disable.
2. Intent. `TransactionLock` may block the calling thread in both APIs. The blocking design itself is not a defect.
3. Intent. Per-UUID cache, lock and channel state may remain allocated for offline UUIDs.
4. Guarantee. The audit log is limited to 500 rows, written after money movement, and its failure may disable the economy.
5. Guarantee. Database causes are hidden from API callers.
6. Intent. The absence of schema constraints is accepted unless current code demonstrably relies on a missing constraint and produces a live failure.
7. Guarantee. `getBaltop` uses a page size of nine.
8. Intent. `playerPayPlayer` is intended for an online payer; do not recommend an online check.
9. Guarantee. Offline inventory and ender-chest shard values are display-oriented and approximate; ordinary staleness is not a defect.
10. Guarantee. Integer overflow may disable the economy.
11. Guarantee. Event delivery is fire-and-forget; dropped shutdown events are not defects.
12. Guarantee. `subtractFromPlayerBankShards` subtracts only bank shards.
13. Intent. A funded database row without playerdata is not assumed realistic.
14. Guarantee. `economyDisabled` is the safety net; a money-moving or inventory-rewriting path that starts while the flag is set is reportable. A flag that flips after a path passed its entry check is not.
15. Intent. `CommonCommandInterlude` restricts player commands to `world`, `world_nether`, `world_the_end`.
16. Intent. Auto-compress and auto-deposit toggles live in Redis, are mutually exclusive, and a Redis failure at startup disables the plugin.
17. Intent. Player commands are refused for console and command-block senders.
18. Intent. Aliases share one executor.
19. Intent. Only the undyed `SHULKER_BOX` material is treated as a currency container, plugin-wide.

## Phase 1: Re-verify every old row

For each row of the old chart, in order:

1. Locate every causing line the row names in the current tree (the numbers will have moved; find the code, not the number).
2. Determine whether the fix commit changed it, and whether the wrong outcome still reproduces. Trace the whole path again; do not stop at the changed line.
3. Classify: `fixed` (the wrong outcome cannot occur), `partially fixed` (some listed findings or entry points remain), `unfixed` (untouched or the change does not remove the outcome), `regressed` (the change removes the outcome but creates a new wrong outcome, which becomes a new row).
4. For a fix, check it against the pitfalls the old chart already flagged, and against these known-bad fix shapes:
   * `AutoDeposit`: cancelling the pickup event and removing the entity later re-fires `playerTouch` and credits again; refusing on the main thread in J contradicts its KDoc; a restore-time divergence check leaves the money moved; stamping the current `DataVersion` while writing only the `Inventory` list leaves every other field unconverted; a suspending semaphore in `TransactionLock` does not fix `runBlocking` on the main thread.
   * A fix that replaces `require` with a `Result.failure` must not route that failure into `handleError`, or every bad argument disables the economy.
   * A fix that moves work off the main thread must keep every Bukkit inventory, entity and scheduler call on the main thread; check each moved line against the async guards in the dependency source.
   * A fix that adds a synchronous main-thread wait inside a coroutine reintroduces the dispatcher starvation the `TransactionLock` row describes.
   * A fix that shuts down the previous balance manager on reload must not cut off transactions in flight against it.

## Phase 2: Fresh audit of changed code

For every file in `git diff 0f2c29a HEAD`, audit the changed functions and everything they call or are called by as if no chart existed, with the same finding discipline as the original audit: every finding names a wrong outcome (incorrect value, lost or duplicated or misdirected value or items, escaped exception, wrong state, blocked operation), the line that causes it, and a fix that changes that line. Pay particular attention to:

* New locks, queues, dispatchers or `runBlocking` calls: trace deadlock cycles between the main thread, `Dispatchers.Default` and jasync callbacks.
* New inventory write paths: which slots, on which thread, against which snapshot, and what the handlers block in the window.
* New SQL: run it against the migration schema in the podman image.
* New Redis usage: connection lifetime, exception handling, thread.
* Changed `plugin.yml`, `config.yml`, `YamlConfig`: required keys that an existing deployed config lacks (an upgrade must not disable the plugin), and dependency ordering.
* Any new recipe, event handler or listener registration: lifecycle across disable, re-enable and `/minecraft:reload`.

A defect the fixes introduced is a row in the new chart regardless of whether the old chart covered its area.

## Phase 3: Merge and self-check

Build the new chart: carried-forward rows rewritten from the current trace (current line numbers, current mechanism), plus new rows. Then check every row against the three questions: what is the wrong outcome, which line causes it, does the fix change that line. Merge rows whose fixes overlap, as the current chart does; a row that only restates another row's cause through a different entry point is deleted.

## Adversarial Review

Hand the merged draft to Codex:

```
codex exec --skip-git-repo-check --model gpt-5.6-sol -c model_reasoning_effort=xhigh -o <scratch>/codex-review.md "<review instructions followed by the full draft>"
```

Confirm the output header shows `model: gpt-5.6-sol` and `reasoning effort: xhigh` before accepting any findings. Give the reviewer the draft, the six surfaces, the Contracts, the Maintainer Requirements, the finding discipline, and the source locations; do not give it the old chart, the delta, or the fix pitfalls list. Ask it to falsify the draft in six buckets: wrong claims, missing entry points or defects (including regressions the fixes introduced), overstated triggers or impact, stale line references, derivative or invented findings, misplaced findings.

Codex is unavailable when `command -v codex` finds nothing, when `codex exec` exits with an authentication, network, quota or unknown-model error and one retry fails the same way, or when the header shows another model or effort after one rerun. A quota error names the next reset time; if that time is within the session, wait for it and retry once. Otherwise run the Cavekit 3 inspector (`ck:inspector`, opus) with the same inputs and record the exact Codex error. Never skip the review; if both reviewers are unavailable, stop and report that.

Verify every reviewer finding against the current source before accepting it. Record the counts of accepted and rejected findings.

## Output format

`DEFECT-CHART.md`:

* Title line.
* The severity key as a three-row table, verbatim from the current chart: 🔴 critical, 🟠 serious, 🟡 minor, with their meanings.
* Six sections in surface order, each a three-column table `Entry point | Severity | Note` (surface 6: `Component | Severity | Note`). Rows ordered by severity within a section. A section with no rows has the heading and no table.
* Each Note: a bold one-sentence statement of what is wrong; the mechanism with current `File.kt:line` references (write `command/AutoCompress.kt` and `command/AutoDeposit.kt` for the command classes, which share basenames with the feature classes); a one-line repro where one exists; then the fix in italics as `*Fix: ...*`. Surface 6 notes begin with `Reached from:` listing every entry point.
* Severity cell holds the glyph and a short plain-language impact tag (for example `🔴 item dup`, `🟠 dead feature`, `🟡 wrong audit value`). No verdict words, no priority codes.
* No header sentences listing what is correct, no methodology, no reviewer discussion, no mention of the previous chart inside rows. Close with one line: `Verified against commit <full hash>.`
* Concise prose, complete sentences, no em dashes or en dashes, no rows for intentional limitations or clean refusals.

`DEFECT-CHART-DELTA.md`:

* One line per old row: the old row's first cell, its status, and the commit hash plus the lines that changed it (or `no change`). For `partially fixed`, name what remains. For `regressed`, name the new row.
* One closing line: the current commit, the reviewer used (`gpt-5.6-sol` at `xhigh`, or `ck:inspector` with the exact Codex error), and the accepted and rejected reviewer finding counts.

Replace the old `DEFECT-CHART.md` only after both files pass the self-check and the review.
