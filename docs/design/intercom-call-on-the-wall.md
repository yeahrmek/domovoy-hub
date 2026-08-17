# Design: the intercom call on the wall

**Scope:** what the panel does when the intercom rings, and why it currently does so little. Not
what Domonap's API is or what its notification contains — that is `docs/domonap.md`, which this doc
leans on throughout and does not repeat.

**Status: open.** The premise this feature was built on turned out to be false, the first repair was
built, measured and withdrawn, and no replacement has been chosen. Nothing here is scheduled.

## The premise that failed

The panel was built to *yield*. `AGENTS.md` still says it: the call "goes on top of whatever is on
screen … our job is to yield to it instantly and come back afterwards", and keeping that takeover
intact is listed as an Always rule. Everything followed from that — `pollPausingForCalls` stops
polling and does nothing else, the listener reads and never acts, and no tile shows a call.

That premise was read off Domonap's manifest on 2026-08-15: `IncomingDomofonCallActivity` declares
`showWhenLocked`, `turnScreenOn` and a full-screen intent, and the app holds
`USE_FULL_SCREEN_INTENT`. It looked settled.

**It is false.** Two calls were captured on the tablet on 2026-08-16 — one with the screen awake and
unlocked, one dozing behind the keyguard — and `IncomingDomofonCallActivity` **did not launch
either time**. Not once in 300,000+ lines of logcat. `mKeyguardOccluded` stayed `false` for the
whole locked ring. What Domonap actually does is take a `SCREEN_BRIGHT_WAKE_LOCK`, wake the tablet
and leave a notification on the lock screen. The call screen is reached only by tapping it.

So the manifest flags say what that activity may do **once started**. They were never evidence that
anything starts it, and the panel's whole design read them as if they were.

The consequence is the gap this doc exists for: **when the intercom rings, the wall panel shows
nothing about it.** It keeps showing tiles. The one thing a hallway panel ought to be good for is
the one thing it does not do.

## What was tried, and what it cost

The obvious repair: since the platform will not launch the call screen, the panel sends the
notification's own `fullScreenIntent` — Domonap's ringing screen, with the intercom video and its
own accept and decline. Never `answerIntent`, never `Открыть дверь`; the panel shows, the person
decides.

Built TDD, installed, rung once with the screen locked. **It worked and it killed the call.** The
activity launched over the keyguard, exactly once, and 416 ms later Telecom logged
`SET_DISCONNECTED` with cause `LOCAL` — the local side hanging up. Nobody had touched the tablet.
An A/B against a reverted build the same evening put `LOCAL` in exactly the run that launched the
screen and `REJECTED` in all three that did not. The full timeline and the A/B table are in
`docs/domonap.md`.

The mechanism is unproven. The leading hypothesis is timing: the panel triggers on the
*notification*, which runs 175–886 ms ahead of the Telecom call, so the screen went up **44 ms
before the call it belongs to was registered** — and Domonap, finding no session, hung up cleanly.

That is a hypothesis with one supporting measurement and no test.

## Where it stands

The listener is read-only again. `DomonapCalls` still decides *when* a call screen would go up —
once per call, on the post that carries the intent, ringing channel only — and nothing acts on it.
That logic is unit-tested and cost nothing to keep; it is the part of the attempt that survived
contact with the tablet.

The trade is not close. A panel that shows nothing while the intercom rings is a poor wall panel. A
panel that hangs up on whoever is at the door is a broken intercom, and the intercom is not ours to
break.

## What could come next

None of these is chosen. Roughly cheapest first:

1. **Delay the send.** One line: wait a few hundred milliseconds, or for the Telecom call to appear,
   before sending the intent. Directly tests the timing hypothesis and costs one ring. If the call
   survives, the feature is back with a constant nobody likes but everybody can measure.
2. **Trigger on Telecom rather than the notification.** The principled version of (1): the call
   screen goes up when the call exists, because that is what it is for. Seeing Telecom's call state
   means becoming an `InCallService` — a manifest change, a new permission surface, and a much
   larger commitment than a notification listener. Worth pricing before it is started, not during.
3. **Show the call on the panel instead of launching anything.** The notification carries the door
   in `android.title`; the panel could put up its own banner — ringing, which door, how long — and
   touch nothing of Domonap's. It cannot show the video, which is most of the value, and it puts an
   apartment identifier on a wall, which is a decision rather than a detail.
4. **Accept the gap.** The notification does appear on the lock screen and the tablet does wake. A
   resident who hears the intercom and looks at the tablet sees *something*, just not from us. This
   is where the panel is today, by omission rather than by choice — and choosing it deliberately is
   a legitimate outcome, as long as `AGENTS.md` stops claiming a takeover that does not happen.

## What has to change regardless

`AGENTS.md` describes the takeover as fact and forbids interfering with it. Two captures say it does
not happen. Whatever is chosen above, that text needs rewriting — it is the project's own
instructions, and it currently teaches the next reader something the tablet disproves.

## Sources

- `docs/domonap.md` — the two captures, the notification record, the withdrawn attempt, the A/B
- Captures taken 2026-08-16, kept outside the repo: they contain the door address and phone handle
