package org.scaladiff

object Diff {

  sealed trait Change

  case object Insert extends Change

  case object Remove extends Change

  case object Keep extends Change

}

/**
 * ...
 *
 * @author Antoine Gourlay
 */
class Diff[A](val a: Array[A], val b: Array[A]) {

  import Diff._

  private case class Point(x: Int, y: Int, changes: List[Change]) {
    def diag = Point(x - 1, y - 1, Keep :: changes)

    def left = Point(x - 1, y, Remove :: changes)

    def up = Point(x, y - 1, Insert :: changes)

    override def toString = s"($x, $y, ${changes.mkString(", ")})"

    override def equals(pt: Any) = {
      if (!pt.isInstanceOf[Point])
        false
      else {
        val ppt = pt.asInstanceOf[Point]
        ppt.x == x && ppt.y == y
      }
    }
  }

  def diff(): Seq[Change] = diff((0, a.length - 1), (0, b.length - 1))


  def diff(rangeA: (Int, Int), rangeB: (Int, Int)): Seq[Change] = {

    // move left (== removals)
    def left(s: Map[Int, Point]): Map[Int, Point] = {
      s.flatMap{ p =>
        if (p._2.x  >= rangeA._1) {
          s.get(p._1 - 1) match {
            case None => List((p._1 - 1, p._2.left))
            case Some(j)=>
              if (p._2.x -1 < j.x)
                List((p._1 - 1, p._2.left))
              else
                Nil
          }
        }
        else
          Nil
      }
    }

    // move up (== insertions)
    def up(s: Map[Int, Point]): Map[Int, Point] = {
      s.flatMap{ p =>
        if (p._2.y  >= rangeB._1) {
          s.get(p._1 + 1) match {
            case None => List((p._1 + 1, p._2.up))
            case Some(j)=>
              if (p._2.y -1 < j.y)
                List((p._1 + 1, p._2.up))
              else
                Nil
          }
        }
        else
          Nil
      }
    }

    // move along a diagonal (== matches)
    def slide(s: Map[Int, Point]): Map[Int, Point] = {
      s.map {
        p =>
          var t: Point = p._2
          while (t.x >= rangeA._1 && t.y >= rangeB._1 && a(t.x) == b(t.y)) {
            t = t.diag
          }
          if (t.x == p._2.x && t.y == p._2.y)
            p
          else
            (p._1, t)
      }
    }

    // start point
    val init = Point(rangeA._2, rangeB._2, Nil)
    // start state
    var state = Map(rangeA._2 - rangeB._2 -> init)

    // end point
    val end = Point(rangeA._1 - 1, rangeB._1 - 1, Nil)

    // first diagonal matching
    state = slide(state)
    //println(state)

    // special case of equal inputs
    var changes: List[Change] = state.head._2.changes

    var stop: Boolean = state.values.exists(p => p.x == rangeA._1 - 1 && p.y == rangeB._1 - 1)

    // let's walk the matrix towards the top-left corner
    while (!stop) {
      // move up the matrix
      val res = slide(state ++ left(state) ++ up(state))

      // should never happen (but who knows...)
      if (res.isEmpty) throw new IllegalStateException()


      res.values.find(_ == end) match {
        case None =>
        case Some(p) =>
          stop = true
          changes = p.changes
      }

      state = res
    }

    changes
  }

  def formatChanges[T](ch: Seq[Change], print: (Change, Int) => T): Seq[T] = {
    var i = -1
    var j = -1

    ch.view.zipWithIndex.map {
      case (Keep, k) =>
        i = i + 1
        j = j + 1
        print(Keep, i)
      case (Insert, k) =>
        j = j + 1
        print(Insert, j)
      case (Remove, k) =>
        i = i + 1
        print(Remove, i)
    }.force
  }
}