/*
 * Designed and developed by 2026 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.skydoves.crayfish.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rubber band a one-finger pan stretches past the image's bounds. */
class CropOverscrollTest {

  @Test
  fun noOverscrollIsNoStretch() {
    assertEquals(0f, rubberBand(0f, 400f))
    assertEquals(0f, inverseRubberBand(0f, 400f))
  }

  @Test
  fun theStretchResistsAndNeverReachesTheDimension() {
    var previous = 0f
    for (overscroll in listOf(1f, 10f, 100f, 1_000f, 100_000f)) {
      val stretch = rubberBand(overscroll, 400f)
      assertTrue(stretch > previous, "the stretch should keep growing, but stopped at $overscroll")
      assertTrue(stretch < overscroll, "the stretch should resist, but $overscroll gave $stretch")
      assertTrue(stretch < 400f, "the stretch should stay under the dimension, but is $stretch")
      previous = stretch
    }
  }

  @Test
  fun theStretchKeepsTheSignOfTheOverscroll() {
    assertEquals(-rubberBand(50f, 400f), rubberBand(-50f, 400f))
  }

  @Test
  fun theInverseUndoesTheStretch() {
    for (overscroll in listOf(-300f, -20f, 5f, 80f, 600f)) {
      val roundTrip = inverseRubberBand(rubberBand(overscroll, 400f), 400f)
      assertEquals(overscroll, roundTrip, absoluteTolerance = 0.01f)
    }
  }

  @Test
  fun garbageInIsNoStretchOut() {
    assertEquals(0f, rubberBand(Float.NaN, 400f))
    assertEquals(0f, rubberBand(50f, 0f))
    assertEquals(0f, inverseRubberBand(Float.POSITIVE_INFINITY, 400f))
    assertTrue(inverseRubberBand(400f, 400f).isFinite(), "the asymptote must invert to a number")
  }
}
