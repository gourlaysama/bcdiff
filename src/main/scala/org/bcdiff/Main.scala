package org.bcdiff

import java.io.{FileFilter, FilenameFilter, File, OutputStreamWriter, StringWriter}
import org.bcdiff.ClassDiffer.{Full, Stat, Shortstat}
import java.util.zip.ZipFile
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Main entry point.
 *
 * @author Antoine Gourlay
 */
object Main extends App {
  private val c = new Conf(args)

  def conf = c

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

  val out = new OutputStreamWriter(System.out)

  val typ = if (c.shortstat()) Shortstat
            else if (c.stat()) Stat
            else               Full

  val classRegex = c.classFilter.filterNot(_.isEmpty).map(_.r).get

  def filterClass(s: Set[String]): Set[String] = {
    def cName(n: String) = n.dropRight(6).replace(File.separatorChar, '.')

    classRegex.fold(s)(r => s.filter(c => r.findFirstIn(cName(c)).isDefined))
  }

  if (f1.isDirectory && f2.isDirectory) {
    val classFilter = new FilenameFilter {
      def accept(dir: File, name: String): Boolean = name.endsWith(".class")
    }
    val dirFilter = new FileFilter {
      def accept(file: File) = file.isDirectory
    }

    def recDir(d: File) = d.listFiles(classFilter).toList

    def classes(d: File): List[String] = d.listFiles(dirFilter).flatMap{ dir =>
      classes(dir).map(c => dir.getName + File.separator + c)
    }.toList ::: d.list(classFilter).toList

    val m = filterClass(classes(f1).toSet ++ classes(f2))

    try {
    val all = m.map{ e => Future {
        val tmpOut = new StringWriter()
        val fi1 = ClassDiffer.FileInfo(new File(f1, e))
        val fi2 = ClassDiffer.FileInfo(new File(f2, e))

        ClassDiffer.diff(fi1, fi2, c.color(), c.methods(), typ, tmpOut)
        tmpOut
    }}.foreach{f =>
      out.append(Await.result(f, Duration.Inf).getBuffer)
      out.flush
    }

    } finally {
      out.flush()
      out.close()
    }
  } else if (f1.getName.endsWith(".jar") && f2.getName.endsWith(".jar")) {
    val ff1 = new ZipFile(f1)
    val ff2 = new ZipFile(f2)

    import scala.collection.JavaConversions._
    try {
    val m = filterClass(ff1.entries.filter(_.getName.endsWith(".class")).filterNot(_.isDirectory).map(_.getName).toSet ++
      ff2.entries.filter(_.getName.endsWith(".class")).filterNot(_.isDirectory).map(_.getName).toSet)
    val all = m.map{ e => Future {
        val tmpOut = new StringWriter()
        val jarpath = s"#!$e"
        val fi1 = new ClassDiffer.FileInfo(Option(ff1.getEntry(e)).map(ff1.getInputStream), f1.getName, f1.getPath + jarpath,
          f1.getAbsolutePath + jarpath)
        val fi2 = new ClassDiffer.FileInfo(Option(ff2.getEntry(e)).map(ff2.getInputStream), f2.getName,
          f2.getPath + jarpath, f2.getAbsolutePath + jarpath)
        ClassDiffer.diff(fi1, fi2, c.color(), c.methods(), typ, tmpOut)
        tmpOut
    }}.foreach{f =>
      out.append(Await.result(f, Duration.Inf).getBuffer)
      out.flush
    }

    } finally {
      out.flush()
      out.close()
      ff1.close()
      ff2.close()
    }

  } else {
    ClassDiffer.diff(f1, f2, c.color(), c.methods(), typ, out)
    out.flush()
    out.close()
  }
}
