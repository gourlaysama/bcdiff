package org.bcdiff

import diff.Diff
import java.io.{InputStream, FileInputStream, File}
import org.objectweb.asm.tree._
import org.objectweb.asm.ClassReader
import Diff._
import ClassDiffer._
import java.io.{Writer, PrintWriter, StringWriter}

object ClassDiffer {

  sealed trait DiffType

  case object Full extends DiffType

  case object Stat extends DiffType

  case object Shortstat extends DiffType

  object FileInfo {
    def apply(in: Option[InputStream], name: String, path: String, abspath: String) =
      new FileInfo(in, name, path, abspath)

    def apply(f: File) = {
      def safeOpen(f: File): Option[InputStream] = if (f.exists()) {
        var r: FileInputStream = null
        try {
          r = new FileInputStream(f)
          Some(r)
        } catch {
          case e: Exception =>
            Console.err.println(s"Failed to open file '${f.getAbsolutePath}'.")
            if (r != null) r.close()
            None
        }
      } else None

      new FileInfo(safeOpen(f), f.getName, f.getPath, f.getAbsolutePath)
    }
  }

  class FileInfo(val in: Option[InputStream], val name: String, val path: String, val abspath: String)

  def diff(f1: File, f2: File, color: Boolean, methods: Boolean, typ: DiffType, output: Writer): Unit =
    diff(FileInfo(f1), FileInfo(f2), color, methods, typ, output)

  def diff(f1: FileInfo, f2: FileInfo, color: Boolean, methods: Boolean, typ: DiffType, output: Writer): Unit =
    new ClassDiffer(f1, f2, color, methods, typ, output).diff()

}

/**
 * Diff two class files.
 *
 * This class does not keep any state.
 *
 * @author Antoine Gourlay
 */
class ClassDiffer(f1: FileInfo, f2: FileInfo, color: Boolean, methods: Boolean, typ: DiffType, val output: Writer) {

  private[this] val out = new PrintWriter(output)

  // reads and makes sure we have valid class files
  private def prepare(): (Option[ClassNode], Option[ClassNode]) = {
    def safeParse(f: FileInfo): Option[ClassNode] = {
      try {
        f.in.map {i =>
          val cn = new ClassNode()
          val cr = new ClassReader(i)
          cr.accept(cn, 0)
          cn
        }
      } catch {
        case e: Exception =>
          Console.err.println(s"Failed to parse class file '${f.abspath}'.")
          None
      } finally {
        f.in.map(_.close())
      }
    }

    (safeParse(f1), safeParse(f2))
  }

  /**
   * Diffs the two class files.
   */
  def diff(): Boolean = {

    implicit val cn@(cn1, cn2) = prepare()



    if (typ == Full) {
      // header
      printHeader()
      val n1 = if (f1.in.isDefined) f1.path else "/dev/null"
      val n2 = if (f2.in.isDefined) f2.path else "/dev/null"
      if (color) {
        removed(s"-- $n1")
        added(s"++ $n2")
      } else {
        out.println(s"--- $n1")
        out.println(s"+++ $n2")
      }

      out.println()

      // basic fields
      compareFieldPretty(_.version)("Bytecode version: " + _)
      compareFieldPretty(_.name)("Name: " + clazzN(_))
      compareFieldPretty(_.superName)("Parent class: " + clazzN(_))

      // advanced fields
      compareAccessFlags(cn1.map(_.access), cn2.map(_.access), ByteCode.class_access_flags)
      compareInterfaces(cn1.map(c => uglyCast(c.interfaces)), cn2.map(c => uglyCast(c.interfaces)))
      compareFieldPretty(_.outerClass)("Outer class: " + clazzN(_))
      // TODO: annotations

      // TODO: fields
      // TODO: attributes
      // TODO: inner classes

      out.println()
    }

    if (methods) {
      // methods
      val methods1 = cn1.toSeq.flatMap(c => uglyCast[MethodNode](c.methods)).map(a => ((a.name, a.desc), a)).toMap
      val methods2 = cn2.toSeq.flatMap(c => uglyCast[MethodNode](c.methods)).map(a => ((a.name, a.desc), a)).toMap

      val same = methods1.keySet.intersect(methods2.keySet)
      val only1 = methods1 -- same
      val only2 = methods2 -- same

      if (only1.size != 0 || only2.size != 0) changes()

      // added / removed methods
      if (typ == Full) {
        val prettyM: ((String, MethodNode)) => String = a =>
        s"Method ${a._2.name} // Signature: ${a._2.desc} | ${a._2.instructions.size()} instructions"

        only1.foreach(a => removed((a._1._2, a._2), prettyM))
        only2.foreach(a => added((a._1._2, a._2), prettyM))
      } else if (typ == Stat) {
        only1.foreach(a => out.println(s"${intPrint(a._2.instructions.size())} ---------- | ${a._2.name} // Signature: ${a._2.desc}  ; removed"))
        only2.foreach(a => out.println(s"${intPrint(a._2.instructions.size())} ++++++++++ | ${a._2.name} // Signature: ${a._2.desc}  ; added"))
      }

      // methods with identical name+signature --> diff
      val modcount = same.toSeq.map {
        s => diffMethods(methods1(s), methods2(s))
      }.count(_ == true)

      if (typ != Full && changed)
        out.println(s"${only1.size} methods removed, ${only2.size} added, $modcount modified")
    }
    changed
  }

  private def diffMethods(met1: MethodNode, met2: MethodNode): Boolean = {

    val d = new Diff(met1.instructions, met2.instructions, output)
    val diff = d.diff()
    val accessmodified = getFlags(met1.access, ByteCode.method_access_flags) != getFlags(met2.access, ByteCode.method_access_flags)
    val modified = diff.exists(_ != Keep) || accessmodified

    // print nothing if there are no differences
    if (modified) {
      changes()
      if (typ == Full) {
        out.println()
        out.println(s"@@ Method ${met1.name} // Signature: ${met1.desc}")

        // diff access flags
        compareAccessFlags(Some(met1.access), Some(met2.access), ByteCode.method_access_flags)

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
          (Stream.fill(s)('+'), Stream.fill(10 - s)('-'))
        } else {
          (Stream.empty, Stream.fill(10)(' '))
        }

        if (color)
          out.print(intPrint(total) + " " + Console.GREEN + Console.BOLD + pl.mkString + Console.RED + mn.mkString + Console.RESET)
        else
          out.print(intPrint(total) + " " + pl.mkString + mn.mkString)

        out.print(s" | ${met1.name} // Signature: ${met1.desc}")
        if (accessmodified)
          out.println("  ; access flags changed")
        else
          out.println()
      }
    }

    modified
  }

  private def intPrint(i: Int): String =
    if (i < 10)       s"  $i"
    else if (i < 100) s" $i"
    else              i.toString

  private def getFlags(a: Int, flags: Map[Int, String]): Set[Int] =
    flags.keySet.filter(k => (a & k) != 0)

  private def compareAccessFlags(a1: Option[Int], a2: Option[Int], flags: Map[Int, String]) {

    val v1 = a1.map(a => getFlags(a, flags)).getOrElse(Set.empty)
    val v2 = a2.map(a => getFlags(a, flags)).getOrElse(Set.empty)

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

      } else "Flags: " + _.map(flags.apply).mkString(", ")

      if (!v1.isEmpty) removed(v1, pretty)
      if (!v2.isEmpty) added(v2, pretty)

    }
  }

  private def compareInterfaces(in1: Option[Seq[String]], in2: Option[Seq[String]]) {
    val v1 = in1.map(_.toSet).getOrElse(Set.empty)
    val v2 = in2.map(_.toSet).getOrElse(Set.empty)

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
      } else "Implemented interfaces: " + _.mkString(", ")

      if (!v1.isEmpty) removed(v1, pretty)
      if (!v2.isEmpty) added(v2, pretty)

    }
  }

  private var changed: Boolean = false
  private def changes() {
    if (!changed) {
      printHeader()
      changed = true
    }
  }

  private def printHeader(): Unit = out.println(s"bcdiff ${f1.path} ${f2.path}")

  private def compareFieldPretty[T](f: ClassNode => T)(pretty: T => String)(implicit cn: (Option[ClassNode], Option[ClassNode])) {
    check(cn._1.flatMap(c => Option(f(c))), cn._2.flatMap(c => Option(f(c))))(pretty)
  }

  private def check[T](v1: Option[T], v2: Option[T])(pretty: T => String) {
    if (!v1.exists(p => v2.exists(_ == p))) {
      v1.foreach(removed(_, pretty))
      v2.foreach(added(_, pretty))
    }
  }

  private def toS[T](t: T) = t.toString

  private def added[T](s: T, pretty: T => String = toS[T] _) {
    if (color)
      out.println(Console.GREEN + Console.BOLD + "+" + pretty(s) + Console.RESET)
    else
      out.println("+ " + pretty(s))
  }

  private def removed[T](s: T, pretty: T => String = toS[T] _) {
    if (color)
      out.println(Console.RED + Console.BOLD + "-" + pretty(s) + Console.RESET)
    else
      out.println("- " + pretty(s))
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
