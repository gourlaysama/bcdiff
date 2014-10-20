name := "bcdiff"

version := "0.5-SNAPSHOT"

scalaVersion := "2.10.4"

scalacOptions ++= Seq("-Xlint", "-deprecation", "-feature")

libraryDependencies += "org.ow2.asm" % "asm-tree" % "5.0.3"

libraryDependencies += "org.rogach" %% "scallop" % "0.9.5"

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

buildInfoPackage := "org.bcdiff"

packagerSettings

packageArchetype.java_application

mappings in Universal ++= Seq(
  file("CHANGELOG.md") -> "doc/CHANGELOG",
  file("LICENSE") -> "doc/LICENSE")

