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

import androidx.compose.runtime.Immutable

/**
 * Which of the image's own gestures are live.
 *
 * ## Why the default is none of them
 *
 * A cropper has two things a finger can address: the image and the crop rectangle. Letting both
 * move at once is the richer model and it is what most croppers ship, but it costs the user a
 * reference frame. The picture drifts and rescales while they are trying to place an edge against
 * it, and nothing on screen stays still long enough to aim at.
 *
 * So by default the image sits exactly where an ordinary `Image` would put it, fitted and
 * unmoving, and every gesture belongs to the crop rectangle. The picture under the frame is then
 * the answer to "what will I get", continuously, which is the one question a crop screen exists to
 * answer.
 *
 * Nothing is lost by this. The frame is placed against the source's full extent either way, and the
 * output is decoded from the file at full resolution rather than from what the preview happened to
 * be showing, so a fitted preview does not cap the quality of the result.
 *
 * Turn the gestures back on when the image is the thing being aimed, not the frame:
 *
 * ```kotlin
 * Cropper(state = state, gestures = CropGestures.Zoomable)
 * ```
 *
 * @property pan Whether two fingers drag the image.
 * @property zoom Whether pinching scales the image, and whether a double tap zooms.
 * @property rotate Whether twisting two fingers turns the image. Independent of
 *   [CropState.rotateBy], which a toolbar can always call.
 * @property singleFingerPan Whether one finger that lands outside the crop rectangle, on no handle,
 *   drags the image. Independent of [pan], so a host can keep two fingers for zooming alone and
 *   still let the image be moved. A finger that lands on a handle or inside the frame still
 *   belongs to the frame, so nothing about resizing changes.
 *   Turning it on also changes what the image is held to: instead of the viewport, the image may
 *   slide anywhere that keeps the crop frame covered, so it can be panned at the fitted zoom
 *   rather than only once zoomed in. Two-finger panning follows the same rule while it is on.
 * @property overscroll Whether the image gives at its bounds instead of stopping.
 *   If overscroll is enabled, a pan, a pinch or a fling that reaches an edge will
 *   continue to drag out of bounds with resistance, and will spring back inside its bounds
 *   when finished. The crop frame never moves with the stretch.
 */
@Immutable
public data class CropGestures(
  val pan: Boolean = false,
  val zoom: Boolean = false,
  val rotate: Boolean = false,
  val singleFingerPan: Boolean = false,
  val overscroll: Boolean = false,
) {

  /**
   * Whether any two-finger gesture addresses the image. [singleFingerPan] is deliberately not part
   * of it: this is what decides whether a second finger is claimed, and with only one-finger
   * panning live a pinch would be claimed for nothing and kept from an ancestor that could use it.
   */
  internal val movesImage: Boolean get() = pan || zoom || rotate

  public companion object {
    /** The image is fixed. Only the crop rectangle moves. */
    public val Default: CropGestures = CropGestures()

    /** Pinch and pan, no twist. A straighten control belongs on a toolbar, not on a pinch. */
    public val Zoomable: CropGestures = CropGestures(pan = true, zoom = true)

    /** Everything, free angle twist included. */
    public val All: CropGestures = CropGestures(pan = true, zoom = true, rotate = true)
  }
}
