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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import com.github.skydoves.crayfish.geometry.CropTransform
import com.github.skydoves.crayfish.geometry.FloatPoint
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.withSign

/**
 * How much a one-finger pan gives once the image is past its bounds.
 *
 * The constant iOS scroll views use: the first stretch past the edge follows the finger at about
 * half speed, and the resistance grows from there, so the image can never be pulled further than
 * one [dimension] however far the finger goes.
 */
private const val RUBBER_BAND_COEFFICIENT: Float = 0.55f

/**
 * The share of [rubberBand]'s asymptote that [inverseRubberBand] is willing to invert. The curve
 * only reaches the asymptote at infinity, so the inverse at it is infinite.
 */
private const val MAXIMUM_INVERTIBLE_STRETCH: Float = 0.99f

/**
 * The visible displacement for a finger that is [overscroll] past the edge on an axis of length
 * [dimension].
 */
internal fun rubberBand(overscroll: Float, dimension: Float): Float {
  if (overscroll == 0f || !overscroll.isFinite() || !dimension.isFinite() || dimension <= 0f) {
    return 0f
  }
  val stretch = abs(overscroll) * RUBBER_BAND_COEFFICIENT / dimension
  return ((1f - 1f / (stretch + 1f)) * dimension).withSign(overscroll)
}

/**
 * The finger travel that [rubberBand] turns into [stretched].
 *
 * A pan that starts while the image is still out of bounds, because the finger came down before the
 * spring back finished, has to pick up from where the image is drawn. Starting from the legal
 * position instead would make the image jump on the first move.
 */
internal fun inverseRubberBand(stretched: Float, dimension: Float): Float {
  if (stretched == 0f || !stretched.isFinite() || !dimension.isFinite() || dimension <= 0f) {
    return 0f
  }
  val fraction = (abs(stretched) / dimension).coerceAtMost(MAXIMUM_INVERTIBLE_STRETCH)
  val overscroll = dimension / RUBBER_BAND_COEFFICIENT * (1f / (1f - fraction) - 1f)
  return overscroll.withSign(stretched)
}

/** How firmly the image is pulled back inside its bounds. See [settleCoastFactor]. */
private const val SETTLE_STIFFNESS: Float = Spring.StiffnessMediumLow

/**
 * How far, per unit of velocity, the spring back lets the image coast on an axis that is not out of
 * bounds: `1 / sqrt(stiffness)`, the critically damped spring's own stopping distance. Aiming the
 * spring there turns it into a pure exponential slow-down on that axis rather than a spring that
 * first accelerates towards a target further away, so a diagonal fling that hits one edge bounces
 * off it while still gliding smoothly along it.
 */
private val settleCoastFactor: Float = 1f / sqrt(SETTLE_STIFFNESS)

/**
 * Writes [candidate] as the image's transform, held to its bounds, and when [overscroll] is on lets
 * the part of it past the bounds show through, rubber-banded.
 *
 * Every gesture that moves the image goes through here, so that a pinch picks up a stretch a pan
 * left behind instead of snapping it away. The frame is held to the image's **in-bounds** position,
 * never the stretched one: the stretch is temporary, and shrinking the frame to fit a position the
 * image is about to leave would be a change the user never asked for.
 */
internal fun RealCropState.placeImage(candidate: CropTransform, overscroll: Boolean) {
  val wanted = candidate.sanitized()
  transform = wanted
  // Any of pan, zoom or rotation can uncover a corner of the crop rectangle.
  constrainImageToViewport()
  constrainFrameToImage()
  if (!overscroll) return
  val legal = transform
  val stretch = wanted.offset - legal.offset
  if (stretch.x == 0f && stretch.y == 0f) return
  transform = legal.copy(
    offset = legal.offset + FloatPoint(
      x = rubberBand(stretch.x, viewportSize.width),
      y = rubberBand(stretch.y, viewportSize.height),
    ),
  ).sanitized()
}

/**
 * The transform a gesture would have had to ask for to leave the image drawn where it is now: the
 * inverse of [placeImage]'s rubber band, and the current transform whenever the image is in bounds
 * or [overscroll] is off.
 *
 * Gestures build on this rather than on [RealCropState.transform] because the transform holds the
 * resisted value, and resisting it again every frame would make the edge feel like a wall after
 * all. It is also what lets a finger catch a spring back half way without the image jumping.
 */
internal fun RealCropState.unresistedTransform(overscroll: Boolean): CropTransform {
  val shown = transform
  if (!overscroll) return shown
  val legal = constrainedImageTransform(shown).offset
  val stretch = shown.offset - legal
  if (stretch.x == 0f && stretch.y == 0f) return shown
  return shown.copy(
    offset = legal + FloatPoint(
      x = inverseRubberBand(stretch.x, viewportSize.width),
      y = inverseRubberBand(stretch.y, viewportSize.height),
    ),
  )
}

/**
 * Springs the image back inside its bounds, carrying [velocity] into the motion.
 *
 * The spring runs on the **unresisted** offset and every frame is drawn through [placeImage], so
 * the image moves with the same resistance a finger feels: a fling that hits an edge with speed to
 * spare stretches past it, slows, and comes back, and a release from a stretch returns along the
 * same curve it was pulled out on. No bounce, since overshooting the bounds on the way back would
 * uncover the crop frame a second time.
 */
internal suspend fun RealCropState.animateCropSettle(velocity: Velocity) {
  val safe = finiteVelocity(velocity)
  val from = unresistedTransform(overscroll = true)
  val target = constrainedImageTransform(
    from.copy(
      offset = from.offset + FloatPoint(safe.x, safe.y) * settleCoastFactor,
    ).sanitized(),
  ).offset
  if (from.offset != target || isFlingWorthwhile(safe)) {
    animate(
      typeConverter = Offset.VectorConverter,
      initialValue = Offset(from.offset.x, from.offset.y),
      targetValue = Offset(target.x, target.y),
      initialVelocity = Offset(safe.x, safe.y),
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = SETTLE_STIFFNESS,
      ),
    ) { value, _ ->
      if (value.x.isFinite() && value.y.isFinite()) {
        placeImage(transform.copy(offset = FloatPoint(value.x, value.y)), overscroll = true)
      }
    }
  }
  // Exactly in bounds, not a spring's float of distance from them.
  placeImage(unresistedTransform(overscroll = false), overscroll = false)
}
