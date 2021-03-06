/* Copyright (c) 2013-2015, Antoine Gourlay
 *
 * This file is part of bcdiff and is licensed under
 * the Simplified BSD license (see LICENSE file).
 */
package org.bcdiff

import org.rogach.scallop.ScallopConf
import scala.util.Properties._

/**
 * Command line argument configuration.
 *
 * @author Antoine Gourlay
 */
class Conf(arg: Seq[String]) extends ScallopConf(arg) {
  val jj = System.getProperty("java.version")
  val jvm = System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version")
  version(s"${BuildInfo.name} - JVM bytecode diff tool (c) 2013-2015 Antoine Gourlay\nversion ${BuildInfo.version} ($jj $jvm)")
  banner(
   s"""
      |Usage: ${BuildInfo.name} [options] [file1] [file2]
      |
      |${BuildInfo.name} is a JVM bytecode diff tool.
      |By default, it diffs two class files and prints on the console a readable diff.
      |
      |Options:
    """.stripMargin)
  footer("\nFor the code and bug-tracker, see https://github.com/gourlaysama/bcdiff")

  mainOptions = Seq(classFilter)

  // general options
  val stat = opt[Boolean](descr = "Generate a diffstat.", noshort = true)
  val shortstat = opt[Boolean](descr = "output only the last line of --stat containing the number of added/modified/deleted entries.", noshort = true)
  val color = toggle("color", default = Some(propIsSet("bcdiff.color")), descrYes = "Show colored diff.", descrNo = "Turn off colored diff.", noshort = true)

  val methods = toggle("methods", default = Some(true), descrYes = "Diff the flags and content (byte-codes) of methods (default)",
  descrNo = "Do not diff methods", noshort = true)

  val context = opt[Int]("context", descr = "Number of context lines to show around diffs. Use -1 to show all.", default = Some(3), noshort = true)

  val classFilter = opt[String](descr = "Only process classes whose name match the provided regex.", argName = "regex")

  val methodNameFilter = opt[String](descr = "Only process methods whose name match the provided regex.", argName = "regex")

  val inverse = opt[Boolean]("inverse", short = 'R', default = Some(false), descr = "Inverse the two inputs.")

  val debug = opt[Boolean](hidden = true, noshort = true)

  val files = trailArg[List[String]](descr = "Class files / folders to diff (exactly 2)", required = true)

  val exitCode = opt[Boolean]("exit-code", default = Some(false), descr = "Make the program exit with codes similar to diff(1) (1 if there were differences and 0 otherwise).", noshort = true).map(_ || quiet())

  val quiet = opt[Boolean](descr = "Disable all output of the program. Implies --exit-code.", default = Some(false), noshort = true)

  validate(files) {
    f => f.size match {
      case 2 => Right(Unit)
      case i => Left(s"expected 2 file arguments, found $i. See --help for usage.")
    }
  }
}
