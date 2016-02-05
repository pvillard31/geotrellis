/*
 * Copyright (c) 2014 Azavea.
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

package geotrellis.raster.render.png

import geotrellis.raster.render._

sealed abstract class PngColorEncoding(val n:Byte, val depth:Int)

// greyscale and color opaque rasters
case class GreyPngEncoding(transparent: Option[Int]) extends PngColorEncoding(0, 1)
case class RgbPngEncoding(transparent: Option[Int]) extends PngColorEncoding(2, 3)

// indexed color, using separate rgb and alpha channels
case class IndexedPngEncoding(rgbs:Array[Int], as:Array[Int]) extends PngColorEncoding(3, 1)

// greyscale and color rasters with an alpha byte
case object GreyaPngEncoding extends PngColorEncoding(4, 4)
case object RgbaPngEncoding extends PngColorEncoding(6, 4)


object PngColorEncoding {
  def fromRasterColorClassifier(cc: ColorClassifier): PngColorEncoding = {
    val len = cc.length
    if(len <= 256) {
      val indices = (0 until len).toArray
      val rgbs = new Array[Int](256)
      val as = new Array[Int](256)

      var i = 0
      while (i < len) {
        val c = cc.getColors(i)
        rgbs(i) = c.get
        as(i) = c.alpha
        i += 1
      }
      rgbs(255) = 0
      as(255) = 0
      IndexedPngEncoding(rgbs, as)
    } else {
      var opaque = true
      var grey = true
      var i = 0
      while (i < len) {
        val c = cc.getColors(i)
        opaque &&= c.isOpaque
        grey &&= c.isGrey
        i += 1
      }

      if (grey && opaque) {
        GreyPngEncoding(cc.getNoDataColor.map(_.x))
      } else if (opaque) {
        RgbPngEncoding(cc.getNoDataColor.map(_.x))
      } else if (grey) {
        GreyaPngEncoding
      } else {
        RgbaPngEncoding
      }
    }
  }
}
