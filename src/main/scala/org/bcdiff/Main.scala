package org.bcdiff

import java.io.{FileFilter, FilenameFilter, File}
import org.bcdiff.ClassDiffer.{Full, Stat, Shortstat}

/**
 * Main entry point.
 *
 * @author Antoine Gourlay
 */
object Main extends App {
  val c = new Conf(args)

  val files = c.files().map(new File(_))

  files.filterNot(_.exists()).foreach {
    t =>
      Console.err.println(s"File '${t.getAbsolutePath}' does not exist!")
      sys.exit(1)
  }

  val List(f1, f2) = files

  if (f1.getAbsoluteFile == f2.getAbsoluteFile) {
    Console.err.println("Cannot diff a file/directory with itself!")
    sys.exit(1)
  }

  val typ = if (c.shortstat()) Shortstat
            else if (c.stat()) Stat
            else               Full


  if (f1.isDirectory && f2.isDirectory) {
    val classFilter = new FilenameFilter { def accept(dir: File, name: String): Boolean = name.endsWith(".class") }
    val dirFilter = new FileFilter { def accept(file: File) = file.isDirectory }

    def recDiff(d1: File, d2: File) {
      val m = d1.list(classFilter).toList ::: d2.list(classFilter).toList
      m.foreach(f => new ClassDiffer(new File(d1, f), new File(d2, f), c.color(), c.methods(), typ).diff())

      val d = d1.listFiles(dirFilter).toList ::: d2.listFiles(dirFilter).toList
      d.foreach(f => recDiff(new File(d1, f.getName), new File(d2, f.getName)))
    }

    recDiff(f1, f2)
  } else new ClassDiffer(f1, f2, c.color(), c.methods(), typ).diff()
}
