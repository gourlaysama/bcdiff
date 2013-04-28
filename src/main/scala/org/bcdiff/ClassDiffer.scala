package org.bcdiff

import diff.Diff
import java.io.{FileInputStream, File}
import org.objectweb.asm.tree._
import org.objectweb.asm.ClassReader
import Diff._
import org.bcdiff.ClassDiffer._

object ClassDiffer {

  sealed trait DiffType

  case object Full extends DiffType

  case object Stat extends DiffType

  case object Shortstat extends DiffType

}

/**
 * Diff two class files.
 *
 * This class does not keep any state.
 *
 * @author Antoine Gourlay
 */
class ClassDiffer(f1: File, f2: File, color: Boolean, methods: Boolean, typ: DiffType) {

  // reads and makes sure we have valid class files
  private def prepare(): (ClassNode, ClassNode) = {

    def safeOpen[A](f: File)(content: FileInputStream => A): A = {
      var r: FileInputStream = null
      try {
        r = new FileInputStream(f)
        content(r)
      } catch {
        case e: Exception =>
          Console.err.println(s"Failed to parse class file '${f.getAbsolutePath}'.")
          sys.exit(1)
          null.asInstanceOf[A] // hum... how can I get rid of that?
      } finally {
        if (r != null) {
          r.close()
        }
      }
    }

    def read(i: FileInputStream): ClassNode = {
      val cn = new ClassNode()
      val cr = new ClassReader(i)
      cr.accept(cn, 0)
      cn
    }

    (safeOpen(f1)(read), safeOpen(f2)(read))
  }

  /**
   * Diffs the two class files.
   */
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
      compareFieldPretty(_.version)("Bytecode version: " + _)
      compareFieldPretty(_.name)("Name: " + clazzN(_))
      compareFieldPretty(_.superName)("Parent class: " + clazzN(_))

      // advanced fields
      compareAccessFlags(cn1.access, cn2.access, ByteCode.class_access_flags)
      compareInterfaces(uglyCast(cn1.interfaces), uglyCast(cn2.interfaces))
      compareFieldPretty(_.outerClass)("Outer class: " + clazzN(_))
      // TODO: annotations

      // TODO: fields
      // TODO: attributes
      // TODO: inner classes

      println()
    }

    if (methods) {
      // methods
      val methods1 = uglyCast[MethodNode](cn1.methods).map(a => ((a.name, a.desc), a)).toMap
      val methods2 = uglyCast[MethodNode](cn2.methods).map(a => ((a.name, a.desc), a)).toMap

      val same = methods1.keySet.intersect(methods2.keySet)
      val only1 = methods1 -- same
      val only2 = methods2 -- same

      // added / removed methods
      val prettyM: ((String, MethodNode)) => String = a =>
        s"Method ${a._2.name} // Signature: ${a._2.desc} | ${a._2.instructions.size()} instructions"

      if (typ == Full) {
        only1.foreach(a => removed((a._1._2, a._2), prettyM))
        only2.foreach(a => added((a._1._2, a._2), prettyM))
      }

      // methods with identical name+signature --> diff
      val modcount = same.toSeq.map {
        s => diffMethods(methods1(s), methods2(s))
      }.count(_ == true)

      if (typ != Full) {
        println(s"${only1.size} methods removed, ${only2.size} added, $modcount modified")
      }
    }
  }

  private def diffMethods(met1: MethodNode, met2: MethodNode): Boolean = {


    val d = new Diff(met1.instructions, met2.instructions)
    val diff = d.diff()
    val accessmodified = getFlags(met1.access, ByteCode.method_access_flags) != getFlags(met2.access, ByteCode.method_access_flags)
    val modified = diff.exists(_ != Keep) || accessmodified

    // print nothing if there are no differences
    if (modified) {
      if (typ == Full) {
        println()
        println(s"@@ Method ${met1.name} // Signature: ${met1.desc}")

        // diff access flags
        compareAccessFlags(met1.access, met2.access, ByteCode.method_access_flags)

        // pretty print bytecode diff
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
        val (pl, mn) = if (ins + rem != 0) {
          val s = Math.ceil(10 * ins / total).toInt
          (Stream.fill(s)('+'),
            Stream.fill(10 - s)('-'))
        } else {
          (Stream.empty, Stream.fill(10)(' '))
        }

        def intPrint(i: Int): String = {
          if (i < 10)
            s"  $i"
          else if (i < 100)
            s" $i"
          else
            i.toString
        }

        if (color) {
          print(intPrint(total) + " " + Console.GREEN + Console.BOLD + pl.mkString + Console.RED + mn.mkString + Console.RESET)
        } else {
          print(intPrint(total) + " " + pl.mkString + mn.mkString)
        }
        print(s" | ${met1.name} // Signature: ${met1.desc}")
        if (accessmodified) {
          println("  ; access flags changed")
        } else {
          println()
        }
      }
    }

    modified
  }

  private def getFlags(a: Int, flags: Map[Int, String]): Set[Int] = {
    flags.keySet.filter(k => (a & k) != 0)
  }

  private def compareAccessFlags(a1: Int, a2: Int, flags: Map[Int, String]) {

    val v1 = getFlags(a1, flags)
    val v2 = getFlags(a2, flags)

    if (v1 != v2) {
      val pretty: Set[Int] => String = if (color) {
        val rem = v1 -- v2
        val add = v2 -- v1

        x => "Flags: " + x.map {
          i =>
            if (rem.contains(i))
              Console.UNDERLINED + flags(i) + Console.RESET + Console.RED + Console.BOLD
            else if (add.contains(i))
              Console.UNDERLINED + flags(i) + Console.RESET + Console.GREEN + Console.BOLD
            else
              flags(i)
        }.mkString(", ")

      } else {
        "Flags: " + _.map(flags.apply).mkString(", ")
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
   * Converts a java non-generic list to a scala Seq[T].
   * Thank you ASM for still supporting Java 1.2 and below.
   *
   * A kitten dies every time this method is called.
   */
  private def uglyCast[T](l: java.util.List[_]): Seq[T] = {
    import scala.collection.JavaConverters._
    l.asInstanceOf[java.util.List[T]].asScala
  }
}
