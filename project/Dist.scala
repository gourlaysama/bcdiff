import sbt._
import Keys._
import xerial.sbt.Pack._


object DistBuild {

  val pack2 = TaskKey[File]("pack2")
  val dist = TaskKey[File]("dist")

  lazy val settings: Seq[Setting[_]] = Seq(
    pack2 <<= (NailgunBuild.ngbuild, pack, streams) map pack2,
    dist <<= (pack2, target, name, version, streams) map makeDist,
    packMain := Map("nong" -> "org.bcdiff.Main")
  )

  def pack2(ngFile: File, packDir: File, out: Keys.TaskStreams): File = {
    val target = packDir / "bin" / ngFile.getName

    out.log.info("Copying %s to %s".format( ngFile, target))
    IO.copyFile(ngFile, target, true)
    target.setExecutable(true)

    IO.delete(packDir / "Makefile")

    packDir
  }

  def makeDist(packDir: File, target: File, name: String, version: String, out: TaskStreams): File = {
    val tgz = target / (name + "-" + version + ".tgz")

    val exitCode = Process(List("tar", "-czf", tgz.name, packDir.name), target) ! out.log
    if (exitCode != 0) out.log.error("Failed to create tgz.")
    out.log.info("Created tgz: " + tgz)

    tgz
  }
}