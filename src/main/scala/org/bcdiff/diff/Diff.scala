package org.bcdiff.diff

import org.bcdiff.{JumpOp, LabelOp, ByteCode}
import org.objectweb.asm.Label
import annotation.tailrec

object Diff {

  sealed trait Change

  case object Insert extends Change

  case object Remove extends Change

  case object Keep extends Change

}

/**
 * Diff two byte code arrays.
 *
 * The assumption about the arrays is that they do not contain Labels, Frames or Lines.
 * For each array a and b, (a/b)lab and (a/b)pos represent the mapping Label <-> Position (instruction number).
 *
 * @author Antoine Gourlay
 */
private[bcdiff] class Diff(val a: Array[ByteCode], val b: Array[ByteCode], val alab: Map[Int, Label], val blab: Map[Int, Label], val apos: Map[Label, Int],
            val bpos: Map[Label, Int]) {

  import Diff._

  // equivalent labels found during the first diff pass
  private var eqlabs = Map[Label, Label]()

  // a step in the diff algorithm.
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

  /**
   * Diffs the two bytecode arrays.
   */
  def diff(): Seq[Change] = diff((0, a.length - 1), (0, b.length - 1))


  private def diff(rangeA: (Int, Int), rangeB: (Int, Int), eqlbs: Option[Map[Label, Label]] = None): Seq[Change] = {

    // move left (== removals)
    def left(s: Map[Int, Point]): Map[Int, Point] = {
      s.flatMap {
        p =>
          if (p._2.x >= rangeA._1) {
            s.get(p._1 - 1) match {
              case None => List((p._1 - 1, p._2.left))
              case Some(j) =>
                if (p._2.x - 1 < j.x)
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
      s.flatMap {
        p =>
          if (p._2.y >= rangeB._1) {
            s.get(p._1 + 1) match {
              case None => List((p._1 + 1, p._2.up))
              case Some(j) =>
                if (p._2.y - 1 < j.y)
                  List((p._1 + 1, p._2.up))
                else
                  Nil
            }
          }
          else
            Nil
      }
    }

    // matching test method
    val matches: (ByteCode, ByteCode) => Boolean = eqlbs.map{ m =>
      (a: ByteCode,b: ByteCode) => (a,b) match {
        case (JumpOp(op, l), JumpOp(op2, l2)) if op == op2 =>
          m.get(l).map(_.toString == l2.toString).getOrElse(false)
        case _ => a == b
      }
    }.getOrElse((a: ByteCode, b:ByteCode) => a == b)

    // move along a diagonal (== matches)
    def slide(s: Map[Int, Point]): Map[Int, Point] = {
      s.map {
        p =>
          var t: Point = p._2
          while (t.x >= rangeA._1 && t.y >= rangeB._1 && matches(a(t.x), b(t.y))) {
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

    if (eqlbs.isEmpty) {
      eqlabs = Map.empty

      // accumulate equivalent labels
      @tailrec
      def acc(i: Int, j: Int, ch: List[Change]) {
        (alab.get(i), blab.get(j)) match {
          case (Some(l1), Some(l2)) => eqlabs = eqlabs + (l1 -> l2)
          case _ =>
        }

        ch match {
          case Keep :: t => acc(i + 1, j + 1, t)
          case Insert :: t => acc(i, j + 1, t)
          case Remove :: t => acc(i + 1, j, t)
          case Nil =>

        }
      }

      acc(-1, -1, changes)

      // re-run diff with label-merging information, in order to merge jumps
      // running the whole diff twice is a bit violent, but at least it works...
      diff(rangeA, rangeB, Some(eqlabs))
    } else {
      changes
    }
  }

  /**
   * Pretty prints the changes in the given change sequence.
   *
   * The change sequence is assumed to have been generated by this very instance of Diff.
   * TODO: this can be enforced easily.
   *
   * @param ch a list of change to get from a to b
   * @param color whether the output should be colored
   */
  def formatChanges(ch: Seq[Change], color: Boolean) {

    def added(pos: Int, s: String) {
      if (color) {
        Console.println(Console.GREEN + Console.BOLD + "  + " + Console.RESET + Console.GREEN + intPrint(pos) + ": "
          + Console.BOLD + s + Console.RESET)
      } else {
        println("  + " + intPrint(pos) + ": " + s)
      }
    }

    def removed(pos: Int, s: String) {
      if (color) {
        Console.println(Console.RED + Console.BOLD + "  - " + Console.RESET + Console.RED + intPrint(pos) + ": "
          + Console.BOLD + s + Console.RESET)
      } else {
        println("  - " + intPrint(pos) + ": " + s)
      }
    }

    def intPrint(i: Int): String = {
      if (i < 10)
        s"  $i"
      else if (i < 100)
        s" $i"
      else
        i.toString
    }

    var i = -1
    var j = -1

    ch.map {
      case Keep =>
        i = i + 1
        j = j + 1
        b(j) match {
          case a @ JumpOp(_, l) => println("    " + intPrint(j) + ": " + a + bpos.get(l).orElse(apos.get(l)).map(_.toString + ":").getOrElse("???"))
          case a => println(s"    ${intPrint(j)}: $a")
        }
      case Insert =>
        j = j + 1
        b(j) match {
          case a @ JumpOp(_, l) => added(j, a + bpos.get(l).map(_.toString + ":").getOrElse("???"))
          case a => added(j, a.toString)
        }
      case Remove =>
        i = i + 1
        a(i) match {
            // TODO: find a way to visually differentiate old vs. new instruction number in jumps
          case a @ JumpOp(_, l) => removed(i, a + eqlabs.get(l).flatMap(bpos.get).orElse(apos.get(l)).map(_.toString + ":").getOrElse("???"))
          case a => removed(i, a.toString)
        }
    }
  }


}