import sbt._
import Keys._
import com.typesafe.sbt.SbtStartScript
import xerial.sbt.Pack._
import sbtbuildinfo.Plugin._


object BcDiffBuild extends Build {

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "org.bcdiff",
    version := "0.3-SNAPSHOT",
    scalaVersion := "2.10.1",
    crossPaths := false
  )

  lazy val bcdiff = Project(
    "bcdiff",
    file("."),
    settings = buildSettings ++ buildInfoSettings ++ packSettings ++ SbtStartScript.startScriptForClassesSettings ++
      NailgunBuild.settings ++ Seq(
      libraryDependencies ++= Seq(
        "org.ow2.asm" % "asm-tree" % "4.1",
        "org.rogach" %% "scallop" % "0.8.1"
      ),
      scalacOptions ++= Seq("-feature", "-deprecation", "-Xlint"),
      sourceGenerators in Compile <+= buildInfo,
      buildInfoKeys := Seq[BuildInfoKey](name, version),
      buildInfoPackage <<= organization,
      packMain <<= name(n => Map(n -> "org.bcdiff.Main"))
    )
  )
}