resolvers += "Spray Repo" at "http://repo.spray.io"

libraryDependencies ++= Seq(
  "org.jacoco" % "org.jacoco.core" % "0.6.0.201210061924" artifacts(Artifact("org.jacoco.core", "jar", "jar")),
  "org.jacoco" % "org.jacoco.report" % "0.6.0.201210061924" artifacts(Artifact("org.jacoco.report", "jar", "jar")),
  "com.github.siasia" %% "xsbt-web-plugin" % "0.12.0-0.2.11.1",
  "com.decodified" % "scala-ssh" % "0.6.3" cross CrossVersion.full,
  "org.bouncycastle" % "bcprov-jdk16" % "1.46",
  "com.jcraft" % "jzlib" % "1.1.1"
)

addSbtPlugin("de.johoop" % "jacoco4sbt" % "1.2.4")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.1")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.8.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.9.1")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.2")

addSbtPlugin("com.orrsella" %% "sbt-stats" % "1.0.2")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.2.5")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.0.1")