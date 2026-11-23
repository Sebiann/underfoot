// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sebiann

package org.underfoot.protocol

/**
 * Fallback for pads whose fff1 frames are absent or do not decode as UREVO frames (for example the Sportstech WalkMate): the walk is
 * tracked from standard FTMS data alone. FTMS has no step count, so steps stay null and are estimated from distance and height.
 */
object FtmsOnly {
    /** Below this the belt counts as standing still; FTMS reports speed in 0.01 km/h steps. */
    private const val MOVING_KMH = 0.05

    /**
     * Belt status from one Treadmill Data reading: moving is RUNNING; standing still with time on the clock is PAUSED (the walk stays
     * open); standing still with the clock at zero is IDLE (the pad started its counters over, so a walk in progress ends, and the
     * zeroed counters are dropped). A frame
     * without speed tells nothing about the belt and is UNKNOWN, which the tracker ignores.
     */
    fun telemetry(r: FtmsReading): Telemetry {
        val speed = r.speedKmh
        val status = when {
            speed == null -> BeltStatus.UNKNOWN
            speed >= MOVING_KMH -> BeltStatus.RUNNING
            (r.elapsedSec ?: 0) > 0 -> BeltStatus.PAUSED
            else -> BeltStatus.IDLE
        }
        // The reset counters belong to no walk: passing them on would overwrite the totals of the walk this frame ends.
        if (status == BeltStatus.IDLE) return Telemetry(status, speed)
        return Telemetry(status, speed, distanceM = r.distanceM, steps = null, kcal = r.kcal, elapsedSec = r.elapsedSec)
    }

    /**
     * Fitness Machine Status (0x2ADA): op code 0x02 "stopped or paused by the user" with parameter 0x01 means stopped. Returns true
     * only for that event; everything else (including short or unknown frames) is false. Never throws.
     */
    fun isStopEvent(frame: ByteArray): Boolean =
        frame.size >= 2 && frame[0] == 0x02.toByte() && frame[1] == 0x01.toByte()
}
