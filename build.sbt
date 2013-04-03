name := "bcdiff"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.1"

scalacOptions += "-Xlint"

libraryDependencies += "org.ow2.asm" % "asm-tree" % "4.1"

libraryDependencies += "org.rogach" %% "scallop" % "0.8.1"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"