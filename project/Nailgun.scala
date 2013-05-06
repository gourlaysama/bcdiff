import sbt._
import Keys._


object NailgunBuild {

  val ngCompiler = SettingKey[String]("ng-compiler")
  val ngCompilerOptions = SettingKey[String]("ng-compiler-options")
  val ngbuild = TaskKey[File]("ng-build", "Build the nailgun client")
  val ngSource = SettingKey[File]("ng-source")
  val ngTarget = SettingKey[File]("ng-target")

  lazy val settings: Seq[Setting[_]] = Seq(
    ngbuild <<= (baseDirectory, ngSource, ngTarget, ngCompiler, ngCompilerOptions, streams) map ngBuild,
    ngCompiler := "gcc",
    ngCompilerOptions := "-Wall -pedantic -s -O3",
    ngSource <<= (sourceDirectory / "nailgun/c/ng.c"),
    ngTarget <<= (target / "nailgun/bin/ng")
  )

  def ngBuild(base: File, src: File, tg: File, compiler: String, opts: String, out: Keys.TaskStreams): File = {
    if (src.exists) {
      tg.getParentFile.mkdirs()
      val cmd = "%s %s -o %s %s".format(compiler, opts, tg.getAbsolutePath, src.getAbsolutePath)
      out.log.info("Compiling " + src.getPath)
      out.log.info(cmd)
      Process(cmd, base) ! out.log
    } else {
      out.log.error("Source not found: " + src)
    }

    tg
  }
}