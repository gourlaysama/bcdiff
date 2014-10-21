import sbt._
import Keys._
import Def.Initialize


object Bcdiff {

  val pandoc = TaskKey[File]("pandoc")

  lazy val pandocTask: Initialize[Task[File]] = Def.task {
    import scala.sys.process._

    val targetDir = target.value / "man"
    val sourceDir = (sourceDirectory in Compile).value / "man"
    val log = streams.value.log
    var error = 0
    val plog = ProcessLogger(s => log.info(s), s => log.error(s))

    def p(s: File, t: File) = Process(Seq("pandoc", "-s", "-t", "man", s.absolutePath, "-o", t.absolutePath)) ! plog

    val rebase = Path.rebase(sourceDir, targetDir)
    val sources = (sourceDir ** (-DirectoryFilter)).get
    val mapping = sources.map(f => rebase(f).map(t => (f, t.getParentFile / t.base))).flatten

    targetDir.mkdirs()
    mapping.foreach{m =>
      m._2.getParentFile.mkdirs()
      val ec = p(m._1, m._2)
      if (ec == 0) log.info(s"Generated ${m._2.absolutePath}")
      error += ec
    }

    if (error > 0) log.warn("There were errors trying to build man pages with pandoc. Ignoring them.")

    targetDir
  }
}
