package org.bcdiff

import diff.Diff
import java.io.{FileInputStream, File}
import org.objectweb.asm.tree._
import org.objectweb.asm.{Label, ClassReader}
import collection.JavaConversions
import java.util.ListIterator
import Diff._
import org.bcdiff.ClassDiffer._

object ClassDiffer {

  sealed trait DiffType

  case object Full extends DiffType

  case object Stat extends DiffType

  case object Shortstat extends DiffType

}

/**
 * ...
 *
 * @author Antoine Gourlay
 */
class ClassDiffer(f1: File, f2: File, color: Boolean, typ: DiffType) {

  private def prepare(): (ClassNode, ClassNode) = {
    var i1: FileInputStream = null
    var i2: FileInputStream = null

    try {
      i1 = new FileInputStream(f1)
      i2 = new FileInputStream(f2)

      val cn1 = new ClassNode()
      val cn2 = new ClassNode()
      implicit val cpl = (cn1, cn2)

      try {
        val cr1 = new ClassReader(i1)
        val cr2 = new ClassReader(i2)
        cr1.accept(cn1, 0)
        cr2.accept(cn2, 0)
      } catch {
        case e: Exception => Console.err.println("Failed to parse class files.")
      }

      (cn1, cn2)
    } finally {
      if (i1 != null)
        i1.close()
      if (i2 != null)
        i2.close()
    }
  }

  def diff() {

    implicit val cn@(cn1, cn2) = prepare()

    if (typ == Full) {
      // header
      println(s"bcdiff ${f1.getPath} ${f2.getPath}")
      if (color) {
        removed(s"-- ${f1.getPath}")
        added(s"++ ${f2.getPath}")
      } else {
        println(s"--- ${f1.getPath}")
        println(s"+++ ${f2.getPath}")
      }

      println()

      // basic fields
      compareField(_.version)
      compareFieldPretty(_.name)(n => "Name: " + clazzN(n))
      compareFieldPretty(_.superName)(n => "Parent class: " + clazzN(n))

      // advanced fields
      compareAccessFlags(cn1.access, cn2.access)
      compareInterfaces(uglyCast(cn1.interfaces), uglyCast(cn2.interfaces))
      compareFieldPretty(_.outerClass)(n => "Outer class: " + clazzN(n))
      // TODO: annotations

      // TODO: fields
      // TODO: attributes
      // TODO: inner classes
    }

    // methods
    val methods1 = uglyCast[MethodNode](cn1.methods).map(a => ((a.name, a.desc), a)).toMap;
    val methods2 = uglyCast[MethodNode](cn2.methods).map(a => ((a.name, a.desc), a)).toMap;

    val same = methods1.keySet.intersect(methods2.keySet)
    val only1 = methods1 -- same;
    val only2 = methods2 -- same;

    // added / removed methods
    val prettyM: ((String, MethodNode)) => String = a =>
      s"Method ${a._2.name} // ${a._2.desc} | ${a._2.instructions.size()} instructions"

    if (typ == Full) {
      only1.foreach(a => removed((a._1._2, a._2), prettyM))
      only2.foreach(a => added((a._1._2, a._2), prettyM))
    }

    // methods with identical name+signature --> diff
    val modcount = same.map{s => diffMethods(methods1(s), methods2(s))}.count(_ == true)

    if (typ != Full) {
      println(s"${only1.size} methods removed, ${only2.size} added, $modcount modified")
    }
  }

  private def diffMethods(met1: MethodNode, met2: MethodNode): Boolean =  {
    import JavaConversions._

    // gets the content of the method
    def collectIns(m: MethodNode) = {
      val ins = m.instructions.iterator().asInstanceOf[ListIterator[AbstractInsnNode]].
        filter(t => t.getType != AbstractInsnNode.FRAME && t.getType != AbstractInsnNode.LINE).map(ByteCode.convert).toSeq

      // we will need the mapping from a label to the next element
      var ct = -1
      var idx = Map[Label, Int]()

      ins foreach {
        case LabelOp(l) =>
          idx = idx + (l -> (ct + 1))
        case _ =>
          ct = ct + 1
      }

      val fins = ins.filterNot(_.isInstanceOf[LabelOp]).toArray

      (fins, idx.map(a => (a._2, a._1)).toMap, idx)
    }

    val (in1, lab1, rlab1) = collectIns(met1)
    val (in2, lab2, rlab2) = collectIns(met2)

    val d = new Diff(in1, in2, lab1, lab2, rlab1, rlab2)
    val diff = d.diff()
    val modified = diff.exists(_ != Keep)

    // print nothing if there are no differences
    if (modified) {
      if (typ == Full) {
        println()
        println(s" Method ${met1.name} // ${met1.desc}")

        d.formatChanges(diff, color)
      } else if (typ == Stat) {
        var kp = 0
        var ins = 0
        var rem = 0
        diff.foreach {
          case Keep => kp = kp + 1
          case Insert => ins = ins + 1
          case Remove => rem = rem + 1
        }

        val total = ins + rem
        val addsize = Math.ceil(10 * ins / total).toInt
        val pl = Stream.fill(addsize)('+')
        val mn = Stream.fill(10 - addsize)('-')

        if (color) {
          print("  " + total + " " + Console.GREEN + Console.BOLD + pl.mkString + Console.RED + mn.mkString + Console.RESET)
        } else {
          print("  " + total + " " + pl.mkString + mn.mkString)
        }
        println(s" | Method ${met1.name} // ${met1.desc}")
      }
    }

    modified
  }

  private def compareAccessFlags(a1: Int, a2: Int) {
    def getFlags(a: Int): Set[Int] = {
      ByteCode.access_flags.keySet.filter(k => (a & k) != 0)
    }
    val v1 = getFlags(a1)
    val v2 = getFlags(a2)

    if (v1 != v2) {
      val pretty: Set[Int] => String = if (color) {
        val rem = v1 -- v2
        val add = v2 -- v1
        val int = v1.intersect(v2)

        x => "Flags: " + x.map {
          i =>
            if (rem.contains(i))
              Console.UNDERLINED + ByteCode.access_flags(i) + Console.RESET + Console.RED + Console.BOLD
            else if (add.contains(i))
              Console.UNDERLINED + ByteCode.access_flags(i) + Console.RESET + Console.GREEN + Console.BOLD
            else
              ByteCode.access_flags(i)
        }.mkString(", ")

      } else {
        "Flags: " + _.map(ByteCode.access_flags.apply).mkString(", ")
      }

      if (!v1.isEmpty) removed(v1, pretty)
      if (!v2.isEmpty) added(v2, pretty)

    }
  }

  private def compareInterfaces(in1: Seq[String], in2: Seq[String]) {
    val v1 = in1.toSet
    val v2 = in2.toSet

    if (v1 != v2) {
      val pretty: Set[String] => String = if (color) {
        val rem = v1 -- v2
        val add = v2 -- v1
        val int = v1.intersect(v2)

        x => "Implemented interfaces: " + x.map {
          i =>
            if (rem.contains(i))
              Console.UNDERLINED + clazzN(i) + Console.RESET + Console.RED + Console.BOLD
            else if (add.contains(i))
              Console.UNDERLINED + clazzN(i) + Console.RESET + Console.GREEN + Console.BOLD
            else
              clazzN(i)
        }.mkString(", ")

      } else {
        "Implemented interfaces: " + _.mkString(", ")
      }

      if (!v1.isEmpty) removed(v1, pretty)
      if (!v2.isEmpty) added(v2, pretty)

    }
  }

  private def compareFieldPretty[T](f: ClassNode => T)(pretty: T => String)(implicit cn: (ClassNode, ClassNode)) {
    check(f(cn._1), f(cn._2))(pretty)
  }

  private def compareField[T](f: ClassNode => T)(implicit cn: (ClassNode, ClassNode)) {
    check(f(cn._1), f(cn._2))(_.toString)
  }

  private def check[T](v1: T, v2: T)(pretty: T => String) {
    if (v1 != v2) {
      removed(v1, pretty)
      added(v2, pretty)
    }
  }

  private def toS[T](t: T) = t.toString

  private def added[T](s: T, pretty: T => String = toS[T] _) {
    if (color) {
      Console.println(Console.GREEN + Console.BOLD + "+" + pretty(s) + Console.RESET)
    } else {
      println("+ " + pretty(s))
    }
  }

  private def removed[T](s: T, pretty: T => String = toS[T] _) {
    if (color) {
      Console.println(Console.RED + Console.BOLD + "-" + pretty(s) + Console.RESET)
    } else {
      println("- " + pretty(s))
    }
  }

  private def clazzN(s: String) = s.replace('/', '.')

  /**
   * Converts a java non-generic list to a scala List[T].
   *
   * A kitten dies every time this method is called.
   *
   * @param l
   * @tparam T
   * @return
   */
  private def uglyCast[T](l: java.util.List[_]): Seq[T] = {
    import scala.collection.JavaConverters._
    l.asInstanceOf[java.util.List[T]].asScala
  }
}
