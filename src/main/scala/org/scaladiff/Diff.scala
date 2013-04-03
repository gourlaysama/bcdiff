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
    // WARNING : stupid algorithm

    // move left (== removals)
    def left(s: Set[Point]): Set[Point] = s.filter(_.x >= rangeA._1).map(_.left)


    // move up (== insertions)
    def up(s: Set[Point]): Set[Point] = s.filter(_.y >= rangeB._1).map(_.up)

    // move along a diagonal (== matches)
    def slide(s: Set[Point]): Set[Point] = {
      s.flatMap {
        p =>
          var t: Point = p
          while (t.x >= rangeA._1 && t.y >= rangeB._1 && a(t.x) == b(t.y)) {
            t = t.diag
          }
          if (t.x == p.x && t.y == p.y)
            Nil
          else
            List(t)
      }
    }


    // start point
    val init = Set(Point(rangeA._2, rangeB._2, Nil))
    // end point
    val end = Point(rangeA._1 - 1, rangeB._1 - 1, Nil)

    // first diagonal matching
    var state = slide(init)
    if (state.isEmpty) state = init

    // special case of equal inputs
    var changes: List[Change] = state.head.changes
    var stop: Boolean = state.exists(p => p.x == rangeA._1 - 1 && p.y == rangeB._1 - 1)

    // let's walk the matrix towards the top-left corner
    while (!stop) {
      // move up the matrix
      val res = left(state) ++ up(state) ++ slide(state)

      // should never happen (but who knows...)
      if (res.isEmpty) throw new IllegalStateException()


      res.find(_ == end) match {
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
        i = i + 1;
        j = j + 1;
        print(Keep, i)
      case (Insert, k) =>
        j = j + 1;
        print(Insert, j)
      case (Remove, k) =>
        i = i + 1;
        print(Remove, i)
    }.force
  }
}