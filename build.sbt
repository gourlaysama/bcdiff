import com.typesafe.sbt.SbtStartScript

import xerial.sbt.Pack._

name := "bcdiff"

version := "0.3-SNAPSHOT"

scalaVersion := "2.10.1"

scalacOptions += "-Xlint"

libraryDependencies += "org.ow2.asm" % "asm-tree" % "4.1"

libraryDependencies += "org.rogach" %% "scallop" % "0.8.1"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

seq(SbtStartScript.startScriptForClassesSettings: _*)

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version)

buildInfoPackage := "org.bcdiff"

packSettings

packMain <<= name(n => Map(n -> "org.bcdiff.Main"))