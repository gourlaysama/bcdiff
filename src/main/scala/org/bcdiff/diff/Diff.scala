package org.bcdiff.diff

import org.bcdiff._
import org.objectweb.asm.Label
import annotation.tailrec
import org.objectweb.asm.tree.{InsnList, AbstractInsnNode}
import java.util.ListIterator
import collection.JavaConversions
import java.io.{Writer, PrintWriter}
import Console.{GREEN, RED, MAGENTA, RESET, BOLD}

object Diff {

  sealed trait Change

  case object Insert extends Change

  case object Remove extends Change

  case object Keep extends Change

}

/**
 * Diff two byte code instruction lists from ASM.
 *
 * @author Antoine Gourlay
 */
private[bcdiff] class Diff(ains: InsnList, bins: InsnList, output: Writer) {

  private[this] val out = new PrintWriter(output)

  import Diff._

  // equivalent labels found during the first diff pass
  private var eqlabs = Map[Label, Set[Label]]()

  // the actual data we operate on
  // (list of bytecode, map(line number -> label), map(label -> line number))
  private val (a, alab, apos) = collectIns(ains)
  private val (b, blab, bpos) = collectIns(bins)

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
        ppt.x == x && ppt.y == y // ignore the list of changes when checking for equality!
      }
    }
  }

  // gets the content of the method in the way we want it
  private def collectIns(ml: InsnList) = {
    import JavaConversions._

    val ins = ml.iterator().asInstanceOf[ListIterator[AbstractInsnNode]].
      filter(t => t.getType != AbstractInsnNode.FRAME && t.getType != AbstractInsnNode.LINE).map(ByteCode.convert).toSeq

    // we will need the mapping from a label to the next element
    var ct = -1
    var idx = Map[String, Int]()

    ins foreach {
      case LabelOp(l) =>
        idx = idx + (l.toString -> (ct + 1))
      case _ =>
        ct = ct + 1
    }

    // and we only keep referenced labels
    var idx2 = Map[Label, Int]()

    ins foreach {
      case bc: LabelAwareByteCode =>
        bc.linkedLabels foreach { ll =>
          idx.get(ll.toString).foreach(i => idx2 = idx2 + (ll -> i))
        }
      case _ =>
    }

    val fins = ins.filterNot(_.isInstanceOf[LabelOp]).toArray

    (fins, idx2.map(a => (a._2, a._1)).toMap, idx2)
  }

  /**
   * Diffs the two bytecode arrays.
   */
  def diff(): Array[Change] = diff((0, a.length - 1), (0, b.length - 1)).toArray


  private def diff(rangeA: (Int, Int), rangeB: (Int, Int), eqlbs: Option[Map[Label, Set[Label]]] = None): Seq[Change] = {
    // we start at the last character, going backwards until we reach position (0,0) (or the equivalent in the
    // given ranges)

    if (Main.conf.debug()) {
      if (!apos.isEmpty && eqlbs.isEmpty) println(apos.mkString("Labels a:\n  ", "\n  ", ""))
      if (!bpos.isEmpty && eqlbs.isEmpty) println(bpos.mkString("Labels b:\n  ", "\n  ", ""))
      eqlbs.filter(!_.isEmpty).foreach(m => out.println(m.mkString("Merging labels:\n  ","\n  ","")))
    }

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

    // matching test method:
    // in the case of the second pass, we add the special test for equality for JumpOp (label-aware test)
    val matches: (ByteCode, ByteCode) => Boolean = eqlbs.map {
      m =>
        (a: ByteCode, b: ByteCode) => (a, b) match {
          case (bc: LabelAwareByteCode, bc2: LabelAwareByteCode) => bc.equalIgnoreLabels(bc2) &&
            bc.linkedLabels.map(m.get).flatten.flatten.map(_.toString).toSet.exists(bc2.linkedLabels.map(_.toString).toSet.apply)
          case _ => a == b
        }
    }.getOrElse((a: ByteCode, b: ByteCode) => a == b)

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
    //out.println(state)

    // special case of equal inputs
    var changes: List[Change] = state.head._2.changes

    var stop: Boolean = state.values.exists(_ == end)

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

    // first pass: None, second pass: Some(Map(...))
    if (eqlbs.isEmpty) {
      eqlabs = Map.empty

      // accumulate equivalent labels
      @tailrec
      def acc(i: Int, j: Int, ch: List[Change]) {
        (alab.get(i), blab.get(j)) match {
          case (Some(l1), Some(l2)) =>
              eqlabs = eqlabs + (l1 -> eqlabs.get(l1).fold(Set(l2))(s => s + l2))
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
  def formatChanges(ch: Array[Change], color: Boolean, ctx: Int) {

    val full = ctx == -1

    // init
    val first = ch.indexWhere(_ != Keep)

    if (first == -1) return

    val start = math.max(first - ctx, 0)
    var i = math.max(start - 1, -1)
    var j = math.max(start - 1, -1)
    var pos = math.min(start + ctx, first)

    def added(p: Int, s: String) {
      if (color) {
        out.println(GREEN + BOLD + "+ " + RESET + GREEN + intPrint(p) + ": "
          + BOLD + s + RESET)
      } else {
        out.println("+ " + intPrint(p) + ": " + s)
      }
    }

    def removed(p: Int, s: String) {
      if (color) {
        out.println(RED + BOLD + "- " + RESET + RED + intPrint(p) + ": "
          + BOLD + s + RESET)
      } else {
        out.println("- " + intPrint(p) + ": " + s)
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

    // utility mapping functions for getting label location
    def insertedLabels(l: Label): Option[Int] = bpos.get(l)
    def removedLabels(l: Label): Option[Int] = eqlabs.get(l).flatMap(s => bpos.get(s.head)).orElse(apos.get(l))
    def keptLabels(l: Label): Option[Int] = bpos.get(l).orElse(apos.get(l))

    def keepPrint(idx: Int): Unit = b(idx) match {
      case bc: LabelAwareByteCode => out.println("  " + intPrint(idx) + ": " + bc.toString(keptLabels))
      case c => out.println(s"  ${intPrint(idx)}: $c")
    }

    def print(idx: Int): Unit = {i += 1; j += 1; keepPrint(idx)}


    out.println(s"${MAGENTA}@@ -${math.max(start, 0)} +${math.max(start, 0)}  @@$RESET")
    (start until pos).foreach(print)


    while (pos < ch.length) {
      while(pos < ch.length && ch(pos) != Keep) {
        ch(pos) match {
          case Insert =>
            j += 1
            b(j) match {
              case bc: LabelAwareByteCode => added(j, bc.toString(insertedLabels))
              case c => added(j, c.toString)
            }
          case Remove =>
            i += 1
            a(i) match {
              // TODO: find a way to visually differentiate old vs. new instruction number in jumps
              case bc: LabelAwareByteCode => removed(i, bc.toString(removedLabels))
              case c => removed(i, c.toString)
            }
        }
        pos += 1
      }
      val restart = ch.indexWhere(_ != Keep, pos)
      val len = if (restart == -1) ch.length - pos else restart - pos
      val end = math.min(j + len, b.length - 1)
      if (len > 2*ctx + 1) {
        ((j + 1) to (j + ctx)).foreach(print)
        if (restart != -1) {
          out.println(s"${MAGENTA}@@ -${i + len - 2*ctx + 1} +${end - ctx} ($len)  @@$RESET")
          i += len - 2*ctx
          j = end - ctx
          (j until (j + ctx)).foreach(print)
        }
      } else {
        ((j + 1) to end).foreach(print)

      }
      pos += len
    }
  }
}
