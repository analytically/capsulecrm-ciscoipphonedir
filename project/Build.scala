import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt._
import Keys._
import sbtassembly.AssemblyPlugin.autoImport._

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

  lazy val buildVersion = "1.0.5"

  lazy val root = Project(id = "capsulecrm-ciscoipphonedir", base = file("."))
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    // .settings(formatSettings: _*) - use carefully, screws up XML
    .settings(
    version := buildVersion,
    organization := "uk.co.coen",
    homepage := Some(new URL("https://github.com/analytically/capsulecrm-ciscoipphonedir")),
    description := "Use Capsule CRM on a Cisco IP phone",
    startYear := Some(2013),
    licenses := Seq("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scalaVersion := "2.10.4",
    scalacOptions := Seq(
      "-encoding", "utf8",
      "-unchecked",
      "-feature",
      "-language:postfixOps",
      "-deprecation",
      "-target:jvm-1.7"),
    assemblyJarName in assembly := "capsule-cisco.jar",
    resolvers += Resolver.sonatypeRepo("snapshots"),
    libraryDependencies ++=
      compile(scalalogging, jsonLenses, guava, jsr305, sprayCan, sprayRouting, sprayCaching, sprayClient, akkaSlf4j, logbackClassic)
      ++ test(scalatest, gatling)
  )
}