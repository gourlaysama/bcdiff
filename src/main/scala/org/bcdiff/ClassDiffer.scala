package org.bcdiff

import diff.Diff
import java.io.{InputStream, FileInputStream, File}
import org.objectweb.asm.tree._
import org.objectweb.asm.ClassReader
import Diff._
import ClassDiffer._
import java.io.{Writer, PrintWriter, StringWriter}
import Console.{RED, GREEN, RESET, BOLD, YELLOW}

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

  def diff(f1: File, f2: File, color: Boolean, methods: Boolean, typ: DiffType, output: Writer, showHeaders: Boolean): Boolean =
    diff(FileInfo(f1), FileInfo(f2), color, methods, typ, output, showHeaders)

  def diff(f1: FileInfo, f2: FileInfo, color: Boolean, methods: Boolean, typ: DiffType, output: Writer, showHeaders: Boolean): Boolean =
    new ClassDiffer(f1, f2, color, methods, typ, output, showHeaders).diff()

}

/**
 * Diff two class files.
 *
 * This class does not keep any state.
 *
 * @author Antoine Gourlay
 */
class ClassDiffer(f1: FileInfo, f2: FileInfo, color: Boolean, methods: Boolean, typ: DiffType, val output: Writer, showHeaders: Boolean) {

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

    if (showHeaders) {
      if (color)
        out.println(s"${YELLOW}bcdiff ${f1.path} ${f2.path}$RESET")
      else
        out.println(s"bcdiff ${f1.path} ${f2.path}")
    }


    if (typ == Full) {
      val n1 = if (f1.in.isDefined) f1.path else "/dev/null"
      val n2 = if (f2.in.isDefined) f2.path else "/dev/null"
      if (color) {
        out.println(s"${YELLOW}--- $n1$RESET")
        out.println(s"${YELLOW}+++ $n2$RESET")
      } else {
        out.println(s"--- $n1")
        out.println(s"+++ $n2")
      }

      out.println()

      // basic fields
      compareFieldPretty(_.version)("Bytecode version: ", _.toString)
      compareFieldPretty(_.name)("Class Name: ", clazzN)
      compareFieldPretty(_.superName)("Parent class: ", clazzN)

      // advanced fields
      compareAccessFlags(cn1.map(_.access), cn2.map(_.access), ByteCode.class_access_flags)
      compareInterfaces(cn1.map(c => uglyCast(c.interfaces)), cn2.map(c => uglyCast(c.interfaces)))
      compareFieldPretty(_.outerClass)("Outer class: ",clazzN)
      compareFieldPretty(_.outerMethod)("Outer method: ", s => s)
      // TODO: annotations

      compareFields(cn1.map(c => uglyCast(c.fields)), cn2.map(c => uglyCast(c.fields)))
      // TODO: attributes
      compareInnerClasses(cn1.map(_.name).getOrElse(""), cn1.map(c => uglyCast(c.innerClasses)), cn2.map(_.name).getOrElse(""), cn2.map(c => uglyCast(c.innerClasses)))
    }

    if (methods) {
      // methods
      val nameFilter: MethodNode => Boolean = Main.conf.methodNameFilter.map(_.r).get.fold((_: MethodNode) => true)(r => (c: MethodNode) => r.unapplySeq(c.name).isDefined)

      val methods1 = cn1.toSeq.flatMap(c => uglyCast[MethodNode](c.methods)).filter(nameFilter).map(a => ((a.name, a.desc), a)).toMap
      val methods2 = cn2.toSeq.flatMap(c => uglyCast[MethodNode](c.methods)).filter(nameFilter).map(a => ((a.name, a.desc), a)).toMap

      val same = methods1.keySet.intersect(methods2.keySet)
      val only1 = methods1 -- same
      val only2 = methods2 -- same

      if (only1.size != 0 || only2.size != 0) changes()

      // added / removed methods
      if (typ == Full) {
        val prettyM: ((String, MethodNode)) => String = a =>
        s"${a._2.name} // Signature: ${a._2.desc} | ${a._2.instructions.size()} instructions"

        only1.foreach(a => removed((a._1._2, a._2), "Method ", prettyM))
        only2.foreach(a => added((a._1._2, a._2), "Method ", prettyM))
      } else if (typ == Stat) {
        if (color) {
          import Console.{GREEN, RED, BOLD, RESET}
          only1.foreach(a => out.println(s"${intPrint(a._2.instructions.size())} $BOLD$RED----------$RESET | ${a._2.name} // Signature: ${a._2.desc}  $BOLD${RED}removed$RESET"))
          only2.foreach(a => out.println(s"${intPrint(a._2.instructions.size())} $BOLD$GREEN++++++++++$RESET | ${a._2.name} // Signature: ${a._2.desc}  $BOLD${GREEN}added$RESET"))
        } else {
          only1.foreach(a => out.println(s"${intPrint(a._2.instructions.size())} ---------- | ${a._2.name} // Signature: ${a._2.desc}  removed"))
          only2.foreach(a => out.println(s"${intPrint(a._2.instructions.size())} ++++++++++ | ${a._2.name} // Signature: ${a._2.desc}  added"))
        }
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
        out.println(s"Method ${met1.name} // Signature: ${met1.desc}")

        // diff access flags
        compareAccessFlags(Some(met1.access), Some(met2.access), ByteCode.method_access_flags)

        // pretty print bytecode diff
        d.formatChanges(diff, color, Main.conf.context())
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

  private def showFlags(a: Int, flags: Map[Int, String]): String = {
    val fl = getFlags(a, flags).map(flags.apply)
    if (fl.isEmpty) "" else fl.mkString("Flags: ", ", ", "")
  }

  private def compareAccessFlags(a1: Option[Int], a2: Option[Int], flags: Map[Int, String], header: String = "") {

    val v1 = a1.map(a => getFlags(a, flags)).getOrElse(Set.empty)
    val v2 = a2.map(a => getFlags(a, flags)).getOrElse(Set.empty)

    if (v1 != v2) {
      val pretty: Set[Int] => String = if (color) {
        val rem = v1 -- v2
        val add = v2 -- v1

        x => x.map {
          i =>
            if (rem.contains(i))
              Console.UNDERLINED + flags(i) + Console.RESET + Console.RED + Console.BOLD
            else if (add.contains(i))
              Console.UNDERLINED + flags(i) + Console.RESET + Console.GREEN + Console.BOLD
            else
              flags(i)
        }.mkString(", ")

      } else _.map(flags.apply).mkString(", ")

      if (!v1.isEmpty) removed(v1, header + "Flags: ", pretty)
      if (!v2.isEmpty) added(v2, header + "Flags: ", pretty)

    }
  }

  private def compareInterfaces(in1: Option[Seq[String]], in2: Option[Seq[String]]) {
    val v1 = in1.map(_.toSet).getOrElse(Set.empty)
    val v2 = in2.map(_.toSet).getOrElse(Set.empty)

    if (v1 != v2) {
      val pretty: Set[String] => String = if (color) {
        val rem = v1 -- v2
        val add = v2 -- v1

        x => x.map {
          i =>
            if (rem.contains(i))
              Console.UNDERLINED + clazzN(i) + Console.RESET + Console.RED + Console.BOLD
            else if (add.contains(i))
              Console.UNDERLINED + clazzN(i) + Console.RESET + Console.GREEN + Console.BOLD
            else
              clazzN(i)
        }.mkString(", ")
      } else _.mkString(", ")

      if (!v1.isEmpty) removed(v1, "Implemented interfaces: ", pretty)
      if (!v2.isEmpty) added(v2, "Implemented interfaces: ",  pretty)

    }
  }

  private def compareInnerClasses(name1: String, in1: Option[Seq[InnerClassNode]], name2: String, in2: Option[Seq[InnerClassNode]]) {
    val v1 = in1.map(_.filter(_.outerName == name1).map(i => (i.innerName, i)).toMap).getOrElse(Map.empty)
    val v2 = in2.map(_.filter(_.outerName == name2).map(i => (i.innerName, i)).toMap).getOrElse(Map.empty)

    if (!v1.isEmpty || !v2.isEmpty) changes()

    if (v1.keySet != v2.keySet) {
      val pretty: Set[String] => String = if (color) {
        val rem = v1 -- v2.keySet
        val add = v2 -- v1.keySet

        x => x.map {
          i =>
            if (rem.contains(i))
              Console.UNDERLINED + clazzN(i) + Console.RESET + Console.RED + Console.BOLD
            else if (add.contains(i))
              Console.UNDERLINED + clazzN(i) + Console.RESET + Console.GREEN + Console.BOLD
            else
              clazzN(i)
        }.mkString(", ")
      } else _.mkString(", ")

      if (!v1.isEmpty) removed(v1.keySet, "Inner classes: ", pretty)
      if (!v2.isEmpty) added(v2.keySet, "Inner classes: ",  pretty)

    }
  }

  private def compareFields(f1: Option[Seq[FieldNode]], f2: Option[Seq[FieldNode]]): Unit = {
    val fi1 = f1.map(_.map(f => ((f.name, f.desc), f)).toMap).getOrElse(Map.empty)
    val fi2 = f2.map(_.map(f => ((f.name, f.desc), f)).toMap).getOrElse(Map.empty)

    val common = fi1.keySet.intersect(fi2.keySet)
    val only1 = fi1 -- common
    val only2 = fi2 -- common

    if (only1.size != 0 || only2.size != 0) changes()

    if (typ == Full) {
      val prettyM: (FieldNode) => String = a =>
      s"${a.name} = ${a.value} // Type: ${a.desc}"

      val fullPrettyM: (FieldNode) => String = a => prettyM(a) + "; " + showFlags(a.access, ByteCode.field_access_flags)

      only1.foreach(a => removed(a._2, "Field ", fullPrettyM))
      only2.foreach(a => added(a._2, "Field ", fullPrettyM))

      common.foreach{ c =>
        val n1 = fi1(c)
        val n2 = fi2(c)
        if (n1.desc != n2.desc || n1.value != n2.value) {
          changes()
          removed(n1, "Field ", prettyM)
          added(n2, "Field ", prettyM)
        } else compareAccessFlags(Some(n1.access), Some(n2.access), ByteCode.field_access_flags, "Field " + prettyM(n1) + "; ")
      }
    }
  }

  private var changed: Boolean = false
  private def changes() {
    if (!changed) {
      changed = true
    }
  }

  private def compareFieldPretty[T](f: ClassNode => T)(header: String, pretty: T => String)(implicit cn: (Option[ClassNode], Option[ClassNode])) {
    check(cn._1.flatMap(c => Option(f(c))), cn._2.flatMap(c => Option(f(c))))(header, pretty)
  }

  private def check[T](v1: Option[T], v2: Option[T])(header: String, pretty: T => String) {
    if (!v1.exists(p => v2.exists(_ == p))) {
      v1.foreach(removed(_, header, pretty))
      v2.foreach(added(_, header, pretty))
    }
  }

  private def toS[T](t: T) = t.toString

  private def added[T](s: T, header: String = "", pretty: T => String = toS[T] _) {
    if (color)
      out.println(GREEN + BOLD + "+" + RESET + header + GREEN + BOLD + pretty(s) + RESET)
    else
      out.println("+ " + pretty(s))
  }

  private def removed[T](s: T, header: String = "", pretty: T => String = toS[T] _) {
    if (color)
      out.println(RED + BOLD + "-" + RESET + header + RED + BOLD + pretty(s) + RESET)
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
