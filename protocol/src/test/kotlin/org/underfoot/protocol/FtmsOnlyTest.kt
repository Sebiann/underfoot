// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sebiann

package org.underfoot.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtmsOnlyTest {
    @Test fun movingBeltIsRunning() {
        val t = FtmsOnly.telemetry(FtmsReading(3.2, 40.0, 2.0, 45))
        assertEquals(BeltStatus.RUNNING, t.status)
        assertEquals(3.2, t.speedKmh!!, 1e-9); assertEquals(40.0, t.distanceM!!, 1e-9); assertEquals(45, t.elapsedSec)
        assertNull(t.steps)                                   // FTMS carries no steps: they are estimated later
    }

    @Test fun stillBeltWithTimeIsPausedAndWithoutTimeIsIdle() {
        assertEquals(BeltStatus.PAUSED, FtmsOnly.telemetry(FtmsReading(0.0, 40.0, 2.0, 45)).status)
        val idle = FtmsOnly.telemetry(FtmsReading(0.0, 0.0, 0.0, 0))
        assertEquals(BeltStatus.IDLE, idle.status)
        assertNull(idle.distanceM); assertNull(idle.kcal); assertNull(idle.elapsedSec)   // zeroed counters must not overwrite the walk's totals
        assertEquals(BeltStatus.IDLE, FtmsOnly.telemetry(FtmsReading(0.0, null, null, null)).status)
    }

    @Test fun noSpeedIsUnknown() {
        assertEquals(BeltStatus.UNKNOWN, FtmsOnly.telemetry(FtmsReading(null, 5.0, null, 10)).status)
    }

    @Test fun stopEventIsOnlyStoppedByUser() {
        assertTrue(FtmsOnly.isStopEvent(byteArrayOf(0x02, 0x01)))
        assertFalse(FtmsOnly.isStopEvent(byteArrayOf(0x02, 0x02)))     // paused
        assertFalse(FtmsOnly.isStopEvent(byteArrayOf(0x04)))           // started or resumed
        assertFalse(FtmsOnly.isStopEvent(byteArrayOf(0x02)))
        assertFalse(FtmsOnly.isStopEvent(ByteArray(0)))
    }

    @Test fun aWalkTrackedFromFtmsAloneIsSavedWithPadDistanceAndNoPadSteps() {
        val tr = SessionTracker()
        for (s in 0..30) tr.onTelemetry(FtmsOnly.telemetry(FtmsReading(3.6, s * 1.0, s / 10.0, s)), s * 1000L)
        val out = assertNotNull(tr.onTelemetry(FtmsOnly.telemetry(FtmsReading(0.0, 0.0, 0.0, 0)), 31_000))
        assertEquals(30.0, out.deviceDistanceM!!, 1e-9)
        assertNull(out.deviceSteps)
        assertEquals(30L, out.activeSec)
        val f = out.finalize(Profile(70.0, 170.0))
        assertEquals(Source.ESTIMATED, f.stepsSource)
        assertTrue(f.steps > 0)
    }

    @Test fun garbageFtmsFramesNeverThrowOrStartAWalk() {
        val tr = SessionTracker()
        val frames = listOf(ByteArray(0), byteArrayOf(0x00), byteArrayOf(0x00, 0x00, 0x7F), byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x01))
        for ((i, f) in frames.withIndex()) UrevoDriver.decodeFtms(f)?.let { tr.onTelemetry(FtmsOnly.telemetry(it), i * 1000L) }
        assertFalse(tr.isActive)
    }
}
