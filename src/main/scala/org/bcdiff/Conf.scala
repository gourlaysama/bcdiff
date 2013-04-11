package org.bcdiff

import org.rogach.scallop.ScallopConf

/**
 * ...
 *
 * @author Antoine Gourlay
 */
class Conf(arg: Seq[String]) extends ScallopConf(arg) {
  val jj = System.getProperty("java.version")
  val jvm = System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version")
  version(s"bcdiff - JVM bytecode diff tool (c) 2013 Antoine Gourlay\nversion 0.1-SNAPSHOT ($jj $jvm)")
  banner(
    """
      |Usage: bcdiff [options] file1.class file2.class
      |
      |bcdiff is a JVM bytecode diff tool.
      |By default, it diffs two class files and prints on the console a readable diff.
      |
      |Options:
    """.stripMargin)
  footer("\nFor the code and bug-tracker, see https://github.com/gourlaysama/bcdiff")

  // options
  val stat = opt[Boolean](descr = "Generate a diffstat.", noshort = true)
  val shortstat = opt[Boolean](descr = "output only the last line of --stat containing the number of added/modified/deleted entries.", noshort = true)
  val constantpool = opt[Boolean](default = Some(true), descr = "Diff the contents of the constant pool.",
    noshort = true)
  val color = opt[Boolean]("color", default = Some(false), descr = "Show colored diff.")

  val files = trailArg[List[String]](descr = "Class files to diff (exactly 2)", required = true)

  validate(files) {
    f => f.size match {
      case 2 => Right(Unit)
      case i => Left(s"expected 2 file arguments, found $i. See --help for usage.")
    }
  }
}