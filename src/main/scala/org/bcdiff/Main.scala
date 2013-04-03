package org.bcdiff

import org.rogach.scallop.ScallopConf
import java.io.File

/**
 * ..
 *
 * @author Antoine Gourlay
 */
object Main extends App {
  val c = new Conf(args)

  val files = c.files().map(new File(_))

  files.filterNot(_.exists()) match {
    case Nil =>
    case t =>
      t.map(_.getAbsolutePath).foreach {n => Console.err.println(s"File '$n' does not exist!")}
      sys.exit(1)
  }

  val List(f1, f2) = files

  if (f1.getAbsoluteFile == f2.getAbsoluteFile) {
    Console.err.println("Cannot diff a file with itself!")
    sys.exit(1)
  }

  val di = new ClassDiffer(f1,f2, c.color())

  di.diff()
}
