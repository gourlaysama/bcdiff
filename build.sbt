name := "bcdiff"

version := "0.4-SNAPSHOT"

scalaVersion := "2.10.3"

scalacOptions += "-Xlint"

libraryDependencies += "org.ow2.asm" % "asm-tree" % "4.1"

libraryDependencies += "org.rogach" %% "scallop" % "0.8.1"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage := "org.bcdiff"

packagerSettings

packageArchetype.java_application

mappings in Universal ++= Seq(
  file("CHANGELOG.md") -> "doc/CHANGELOG",
  file("LICENSE") -> "doc/LICENSE")

