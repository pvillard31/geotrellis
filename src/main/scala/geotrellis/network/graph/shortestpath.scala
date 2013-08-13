package geotrellis.network.graph

import geotrellis.network._

import scala.collection.mutable.{ListBuffer,PriorityQueue}

import spire.syntax._

abstract sealed class PathType

case object WalkPath extends PathType
case object BikePath extends PathType 
case object TransitPath extends PathType // includes walking

case class PathEdge(vertex:Int,duration:Int) {
  def toStr = {
    s"Path(Vertex: $vertex,Duration: $duration seconds)"
  }
}

object ShortestPathTree {
  // Optimization: Set an empty SPT array once you know the 
  // graph vertex count, since Array.clone is so much faster than
  // Array.fill
  private var _sptArray:Array[Int] = null
  def initSptArray(vertexCount:Int) = { _sptArray = Array.fill[Int](vertexCount)(-1) }

  def apply(from:Int, startTime:Time, graph:TransitGraph) = 
    new ShortestPathTree(from,startTime,graph,None,TransitPath)

  def apply(from:Int,startTime:Time,graph:TransitGraph,maxDuration:Duration) =
    new ShortestPathTree(from,startTime,graph,Some(maxDuration),TransitPath)

  def apply(from:Int,startTime:Time,graph:TransitGraph,maxDuration:Duration,pathType:PathType) =
    new ShortestPathTree(from,startTime,graph,Some(maxDuration),pathType)
}

class ShortestPathTree(val startVertex:Int,
                       val startTime:Time,
                       graph:TransitGraph,
                       val maxDuration:Option[Duration],
                       pathType:PathType) {
  /**
   * Array containing arrival times of the current shortest
   * path to the index vertex.
   */
  private val shortestPathTimes = 
    if(ShortestPathTree._sptArray != null) { ShortestPathTree._sptArray.clone }
    else { Array.fill[Int](graph.vertexCount)(-1) }

  private val _reachableVertices = 
    ListBuffer[Int]()

  def reachableVertices:Set[Int] = _reachableVertices.toSet

  shortestPathTimes(startVertex) = 0

  // dijkstra's

  object VertexOrdering extends Ordering[Int] {
    def compare(a:Int, b:Int) = {
      val sA = shortestPathTimes(a)
      val sB = shortestPathTimes(b)
      if(sA == 0) { -1 }
      else if(sB == 0) { 1 }
      else {
        -(sA compare sB)
      }
    }
  }

  val queue = PriorityQueue[Int]()(VertexOrdering)

  val tripStart = startTime.toInt
  val duration = maxDuration.getOrElse(Duration(Int.MaxValue)).toInt + tripStart

  val foreachEdge:(Int,Int,(Int,Int)=>Unit)=>Unit = { (sv, t, f) =>
    pathType match {
      case WalkPath =>
        graph.foreachWalkEdge(sv)(f)
      case BikePath =>
        graph.foreachBikeEdge(sv)(f)
      case TransitPath =>
        graph.foreachTransitEdge(sv,t)(f)
    }
  }

  foreachEdge(startVertex,tripStart, { (target,weight) =>
    val t = tripStart + weight
    if(t <= duration) {
      shortestPathTimes(target) = t
      queue += target
      _reachableVertices += target
    }
  })

  while(!queue.isEmpty) {
    val currentVertex = queue.dequeue
    val currentTime = shortestPathTimes(currentVertex)

    foreachEdge(currentVertex, currentTime, { (target,weight) =>
      val t = currentTime + weight
      if(t <= duration) {
        val currentTime = shortestPathTimes(target)
        if(currentTime == -1 || currentTime > t) {
          _reachableVertices += target
          shortestPathTimes(target) = t
          queue += target
        }
      }
    })
  }

  def travelTimeTo(target:Int):Duration = {
    new Duration(shortestPathTimes(target) - startTime.toInt)
  }
}
