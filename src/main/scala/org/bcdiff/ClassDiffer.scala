package org.bcdiff

import java.io.{OutputStreamWriter, FileInputStream, File}
import org.objectweb.asm.tree._
import org.objectweb.asm.ClassReader
import collection.{JavaConverters, JavaConversions}
import org.scaladiff.Diff
import java.util
import org.scaladiff.Diff._

/**
 * ...
 *
 * @author Antoine Gourlay
 */
class ClassDiffer(f1: File, f2: File, color: Boolean) {

  def diff() {
    val i1 = new FileInputStream(f1)
    val i2 = new FileInputStream(f2)

    val cn1 = new ClassNode()
    val cn2 = new ClassNode()
    implicit val cpl = (cn1, cn2)

    val cr1 = new ClassReader(i1)
    val cr2 = new ClassReader(i2)
    cr1.accept(cn1, 0);
    cr2.accept(cn2, 0);
    i1.close();
    i1.close();

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

    // methods
    val methods1 = uglyCast[MethodNode](cn1.methods).map(a => ((a.name, a.desc), a)).toMap;
    val methods2 = uglyCast[MethodNode](cn2.methods).map(a => ((a.name, a.desc), a)).toMap;

    val same = methods1.keySet.intersect(methods2.keySet)
    val only1 = methods1 -- same;
    val only2 = methods2 -- same;

    val prettyM: ((String, MethodNode)) => String = a =>
      s"Method ${a._2.name} | ${a._1} | ${a._2.instructions.size()} instructions"

    only1.foreach(a => removed((a._1._2, a._2), prettyM))
    only2.foreach(a => added((a._1._2, a._2), prettyM))

    for (s <- same) {
      val met1 = methods1(s)
      val met2 = methods2(s)

      import JavaConversions._

      val in1 = met1.instructions.iterator().asInstanceOf[util.ListIterator[AbstractInsnNode]].
        filter(t => t.getType != AbstractInsnNode.FRAME && t.getType != AbstractInsnNode.LINE).map(ByteCode.convert).toArray
      val in2 = met2.instructions.iterator().asInstanceOf[util.ListIterator[AbstractInsnNode]].
        filter(t => t.getType != AbstractInsnNode.FRAME && t.getType != AbstractInsnNode.LINE).map(ByteCode.convert).toArray

      val d = new Diff(in1, in2)
      val diff = d.diff()

      def prettyIns(ch: Change, i: Int) {

        def intPrint(i: Int): String = {
          if (i < 10)
            s"  $i"
          else if (i < 100)
            s" $i"
          else
            i.toString
        }

        ch match {
          case Keep =>
            in2(i) match {
              case _ :LabelOp =>
              case a => println(s"    ${intPrint(i)}: $a")
            }
          case Remove => removed(s"      : ${in1(i)}")
          case Insert => added(s"   ${intPrint(i)}: ${in2(i)}")
        }
      }

      if (diff.exists(_ != Keep)) {
        println()
        println(s" Method ${met1.name} // ${met1.desc}")

        d.formatChanges(diff, prettyIns _)
      }
    }
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

  private def toS[T](t:T) = t.toString

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
