import sbt._
import Keys._
import scala.Some

object Build extends sbt.Build {
  import Dependencies._

  lazy val buildVersion = "1.0.0-SNAPSHOT"

  lazy val root = Project(id = "capsulecrm-ciscoipphonedir", base = file("."))
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    .settings(com.typesafe.sbt.SbtStartScript.startScriptForClassesSettings: _*)
    .settings(
    version := buildVersion,
    organization := "uk.co.coen",
    organizationName := "Coen Recruitment",
    organizationHomepage := Some(new URL("http://www.coen.co.uk")),
    description := "Cisco IP Phone Directory backed by Capsule CRM",
    startYear := Some(2013),
    scalaVersion := "2.10.2",
    scalacOptions := Seq(
      "-encoding", "utf8",
      "-unchecked",
      "-deprecation",
      "-target:jvm-1.6"),
    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += Resolver.typesafeRepo("snapshots"),
    resolvers += Resolver.sonatypeRepo("releases"),
    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers ++= Dependencies.nonStandardRepos,
    shellPrompt := ShellPrompt.buildShellPrompt,
    libraryDependencies ++=
      compile(
        typesafeConfig, scalalogging, akkaActor, jsonLenses,
        sprayCan, sprayRouting, sprayCaching, sprayClient,
        akkaSlf4j, logbackClassic)
  )
}

// Shell prompt which show the current project and git branch
object ShellPrompt {
  object devnull extends ProcessLogger {
    def info(s: => String) {}
    def error(s: => String) {}
    def buffer[T](f: => T): T = f
  }

  val buildShellPrompt = {
    val LGREEN = "\033[1;32m"
    val LBLUE = "\033[01;34m"

    (state: State) => {
      val currProject = Project.extract(state).currentProject.id
      if (System.getProperty("sbt.nologformat", "false") != "true") {
        def currBranch = (
          ("git status -sb" lines_! devnull headOption)
            getOrElse "-" stripPrefix "## "
          )

        "%s%s%s:%s%s%s » ".format(LBLUE, currProject, scala.Console.WHITE, LGREEN, currBranch, scala.Console.WHITE)
      }
      else {
        "%s%s%s » ".format(LBLUE, currProject, scala.Console.WHITE)
      }
    }
  }
}
