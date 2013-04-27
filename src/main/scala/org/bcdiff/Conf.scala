package org.bcdiff

import org.rogach.scallop.ScallopConf

/**
 * Command line argument configuration.
 *
 * @author Antoine Gourlay
 */
class Conf(arg: Seq[String]) extends ScallopConf(arg) {
  val jj = System.getProperty("java.version")
  val jvm = System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version")
  version(s"${BuildInfo.name} - JVM bytecode diff tool (c) 2013 Antoine Gourlay\nversion ${BuildInfo.version} ($jj $jvm)")
  banner(
   s"""
      |Usage: ${BuildInfo.name} [options] file1.class file2.class
      |
      |${BuildInfo.name} is a JVM bytecode diff tool.
      |By default, it diffs two class files and prints on the console a readable diff.
      |
      |Options:
    """.stripMargin)
  footer("\nFor the code and bug-tracker, see https://github.com/gourlaysama/bcdiff")

  // options
  val stat = opt[Boolean](descr = "Generate a diffstat.", noshort = true)
  val shortstat = opt[Boolean](descr = "output only the last line of --stat containing the number of added/modified/deleted entries.", noshort = true)
  val color = toggle("color", default = Some(true), descrYes = "Show colored diff (default).", descrNo = "Turn off colored diff.")

  val files = trailArg[List[String]](descr = "Class files to diff (exactly 2)", required = true)

  validate(files) {
    f => f.size match {
      case 2 => Right(Unit)
      case i => Left(s"expected 2 file arguments, found $i. See --help for usage.")
    }
  }
}
