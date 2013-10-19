name := "bcdiff"

version := "0.3-SNAPSHOT"

scalaVersion := "2.10.2"

scalacOptions += "-Xlint"

libraryDependencies += "org.ow2.asm" % "asm-tree" % "4.1"

libraryDependencies += "org.rogach" %% "scallop" % "0.8.1"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version)

buildInfoPackage := "org.bcdiff"

packagerSettings

packageArchetype.java_application

mappings in Universal ++= Seq(
  file("CHANGELOG.md") -> "doc/CHANGELOG",
  file("LICENSE") -> "doc/LICENSE")

