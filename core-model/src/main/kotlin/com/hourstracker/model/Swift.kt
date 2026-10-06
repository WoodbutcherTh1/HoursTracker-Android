package com.hourstracker.model

import java.time.Instant
import kotlin.math.abs
import kotlin.math.truncate

// Small helpers that reproduce Swift / Foundation numeric behavior, so the pay code can stay a literal
// port. Each one exists because the obvious Kotlin function behaves differently in a way that changes
// pay by a cent or flips the last bits of a Double:
//  - swiftMin / swiftMax: Swift's min/max resolve ties and signed zeros differently from kotlin.math.
//  - roundedAwayFromZero: Swift's `.rounded()`; kotlin.math.round rounds halves to even.
//  - Instant.secondsSince / plusSeconds: Foundation's `Date.timeIntervalSince` / `addingTimeInterval`
//    (a Double number of seconds), exact for the whole-second instants the golden data uses.

/** Swift's `min(x, y)` and `max(x, y)`: ties and signed zeros resolve exactly as in the Swift standard library. */
internal fun swiftMin(x: Double, y: Double): Double = if (y < x) y else x

internal fun swiftMax(x: Double, y: Double): Double = if (y >= x) y else x

/** Swift's `Double.rounded()`: to nearest, halves away from zero. (`kotlin.math.round` rounds halves to even.) */
internal fun roundedAwayFromZero(value: Double): Double {
    if (value.isNaN() || value.isInfinite()) return value
    val whole = truncate(value)
    return if (abs(value - whole) >= 0.5) whole + (if (value < 0) -1.0 else 1.0) else whole
}

/** `Date.timeIntervalSince(_:)`. */
internal fun Instant.secondsSince(other: Instant): Double =
    (epochSecond - other.epochSecond).toDouble() + (nano - other.nano).toDouble() / 1e9

/** `Date.addingTimeInterval(_:)`, exact for whole seconds. */
internal fun Instant.plusSeconds(seconds: Double): Instant {
    val whole = truncate(seconds)
    val nanos = ((seconds - whole) * 1e9).toLong()
    return plusSeconds(whole.toLong()).plusNanos(nanos)
}
