package geotrellis.raster.reproject

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.vector.reproject._
import geotrellis.testkit._
import geotrellis.proj4._

import org.scalacheck._
import Prop._
import Gen._
import Arbitrary._

import spire.syntax.cfor._

trait RowTransformCheck { self: Properties =>
  case class TestCase(extent: Extent, srcX: Array[Double], srcY: Array[Double])

  def genPoint(xmin: Double, ymin: Double, 
               xmax: Double, ymax: Double): Gen[Point] =
    for {
      x <- choose(xmin, xmax)
      y <- choose(ymin, ymax)
    } yield Point(x, y)
}

object RowTransformCheck_LatLngToWebMercator extends Properties("RowTransform") with RowTransformCheck {
  lazy val genExtent: Gen[Extent] = 
    for {
      p1 <- genPoint(-180, -85.0, 180.00, 85.0)
      p2 <- genPoint(-180, -85.0, 180.00, 85.0)
    } yield {
        val (x1, y1) = (p1.x, p1.y)
      val (x2, y2) = (p2.x, p2.y)

      val (xmin, xmax) = 
        if(x1 < x2) (x1, x2) else (x2, x1)

      val (ymin, ymax) =
        if(y1 < y2) (y1, y2) else (y2, y1)

      Extent(xmin, ymin, xmax, ymax)
    }

  lazy val genTestCase: Gen[TestCase] =
    for {
      extent <- genExtent;
      size <- Gen.choose(50,100);
      y <- choose(extent.ymin, extent.ymax)
    } yield {
      val re = RasterExtent(extent, size, size)
      val xs = (0 until size).map { col => re.gridColToMap(col) }.toArray
      TestCase(extent, xs.sorted.toArray, (0 until size).map { i => y }.toArray)
    }

  implicit lazy val arbTestCase: Arbitrary[TestCase] =
    Arbitrary(genTestCase)

  case class Threshold(v: Double)
  lazy val genThreshold: Gen[Threshold] = 
    for {
      v <- choose(0.1, 5.0)
    } yield Threshold(v)

  implicit lazy val arbThreshold: Arbitrary[Threshold] =
    Arbitrary(genThreshold)

  val transform = Transform(LatLng, WebMercator)

  property("Stays within thresholds") = forAll { (testCase: TestCase, thresh: Threshold) =>
    val TestCase(extent, srcX, srcY) = testCase
    val threshold = thresh.v
    val destX = Array.ofDim[Double](srcX.size)
    val destY = destX.clone

    val rowTransform = RowTransform.approximate(transform, threshold)
    rowTransform(srcX, srcY, destX, destY)

    val src =  srcX.zip(srcY).map { case (x, y) => Point(x, y) }
    val srcProjected = src.map(_.reproject(transform))
    val dest = destX.zip(destY).map { case (x, y) => Point(x, y) }

    val result = 
      srcProjected.zip(dest)
        .map { case(p1, p2) =>
          val dx = math.abs(p1.x - p2.x)
          val dy = math.abs(p1.y - p2.y)
          (dx + dy) < threshold
        }
        .foldLeft(true)(_ && _)

    if(!result) {
      println("ERRORED!")
      println(s"Extent: $extent")
      println(s"srcX: ${srcX.toSeq}")
      println(s"srcY: ${srcY.toSeq}")
      println(s"threshold: ${thresh}")
    }
    result
  }
}

object RowTransformCheck_UTMToWebMercator extends Properties("RowTransform") with RowTransformCheck {
  //Bounds: -78.0000, 0.0000, -72.0000, 84.0000
  lazy val genExtent: Gen[Extent] = 
    for {
      p1 <- genPoint(-77.9,0.0,-72.1,83.9)
      p2 <- genPoint(-77.9,0.0,-72.1,83.9)
    } yield {
      val (x1, y1) = (p1.x, p1.y)
      val (x2, y2) = (p2.x, p2.y)

      val (xmin, xmax) = 
        if(x1 < x2) (x1, x2) else (x2, x1)

      val (ymin, ymax) =
        if(y1 < y2) (y1, y2) else (y2, y1)

      Extent(xmin, ymin, xmax, ymax)
    }

  lazy val genTestCase: Gen[TestCase] =
    for {
      extent <- genExtent;
      size <- Gen.choose(50,100);
      y <- choose(extent.ymin, extent.ymax)
    } yield {
      val re = RasterExtent(extent, size, size)
      val xs = (0 until size).map { col => re.gridColToMap(col) }.toArray
      TestCase(extent, xs.sorted.toArray, (0 until size).map { i => y }.toArray)
    }

  implicit lazy val arbTestCase: Arbitrary[TestCase] =
    Arbitrary(genTestCase)

  case class Threshold(v: Double)
  lazy val genThreshold: Gen[Threshold] = 
    for {
      v <- choose(0.0, 5.0)
    } yield Threshold(v)

  implicit lazy val arbThreshold: Arbitrary[Threshold] =
    Arbitrary(genThreshold)

  val utm = CRS.fromName("EPSG:32618")
  val llToUtm = Transform(LatLng, utm)
  val utmToWebMercator = Transform(utm, WebMercator)

  property("Stays within thresholds for EPSG:32618") = forAll { (testCase: TestCase, thresh: Threshold) =>
    val TestCase(extent, lng, lat) = testCase
    val (srcX, srcY) = {
      val reproj = lng.zip(lat).map { case (x, y) => Point(x, y).reproject(llToUtm) }
      // In order to keep the input linear, only take the y value of the first reprojected point.
      (reproj.map(_.x).toArray, (0 until lng.size).map(x => reproj.head.y).toArray)
    }

    val threshold = thresh.v
    val destX = Array.ofDim[Double](srcX.size)
    val destY = destX.clone

    val rowTransform = RowTransform.approximate(utmToWebMercator, threshold)
    rowTransform(srcX, srcY, destX, destY)

    val src =  srcX.zip(srcY).map { case (x, y) => Point(x, y) }
    val srcProjected = src.map(_.reproject(utmToWebMercator))
    val dest = destX.zip(destY).map { case (x, y) => Point(x, y) }

    val result =
      srcProjected.zip(dest)
        .map { case(p1, p2) =>
          val dx = math.abs(p1.x - p2.x)
          val dy = math.abs(p1.y - p2.y)
          val d  = dx + dy
          if(d >= threshold) { println(s"$p1 should be $p2") }
          d < threshold
        }
        .foldLeft(true)(_ && _)

    if(!result) {
      println("ERRORED!")
      println(s"Extent: $extent")
      println(s"srcX: ${srcX.toSeq}")
      println(s"srcY: ${srcY.toSeq}")
      println(s"threshold: ${thresh}")
    }
    result
  }
}
