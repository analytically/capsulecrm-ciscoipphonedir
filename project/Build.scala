import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt._
import Keys._
import sbtassembly.Plugin.AssemblyKeys._
import scala.Some

object Build extends sbt.Build {
  import Dependencies._

  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test := formattingPreferences
  )

  def formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
  }

  lazy val buildVersion = "1.0.3"

  lazy val root = Project(id = "capsulecrm-ciscoipphonedir", base = file("."))
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    .settings(sbtassembly.Plugin.assemblySettings: _*)
    // .settings(formatSettings: _*) - use carefully, screws up XML
    .settings(
    version := buildVersion,
    homepage := Some(new URL("https://github.com/analytically/capsulecrm-ciscoipphonedir")),
    description := "Use Capsule CRM on a Cisco IP phone",
    startYear := Some(2013),
    licenses := Seq("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scalaVersion := "2.10.3",
    scalacOptions := Seq(
      "-encoding", "utf8",
      "-unchecked",
      "-feature",
      "-language:postfixOps",
      "-deprecation",
      "-target:jvm-1.7"),
    jarName in assembly := "capsule-cisco.jar",
    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers += "spray repo" at "http://repo.spray.io",
    libraryDependencies ++=
      compile(scalalogging, jsonLenses, guava, jsr305, sprayCan, sprayRouting, sprayCaching, sprayClient, akkaSlf4j, logbackClassic)
      ++ test(scalatest, gatling)
  )
}