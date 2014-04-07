import sbt._

object Dependencies {
  def compile(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")

  def provided(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")

  def test(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")

  def runtime(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")

  def container(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

  object V {
    val akka = "2.2.4"
    val spray = "1.2.1"
  }

  // Utilities
  val scalalogging = "com.typesafe" %% "scalalogging-slf4j" % "1.1.0"
  val jsonLenses = "net.virtual-void" %%  "json-lenses" % "0.5.4"
  val guava = "com.google.guava" % "guava" % "16.0.1"
  val jsr305 = "com.google.code.findbugs" % "jsr305" % "2.0.3" // see http://stackoverflow.com/questions/10007994/why-do-i-need-jsr305-to-use-guava-in-scala

  // Spray
  val sprayCan = "io.spray" % "spray-can" % V.spray
  val sprayCaching = "io.spray" % "spray-caching" % V.spray
  val sprayRouting = "io.spray" % "spray-routing" % V.spray
  val sprayClient = "io.spray" % "spray-client" % V.spray

  // Akka
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % V.akka

  // Logging
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.1.2"

  // Testing
  val scalatest = "org.scalatest" %% "scalatest" % "2.1.0"
  val gatling = "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.0.0-SNAPSHOT"
}
