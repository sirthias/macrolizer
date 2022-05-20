import sbt._

enablePlugins(AutomateHeaderPlugin)

organization := "io.bullet"
homepage := Some(new URL("https://github.com/sirthias/macrolizer/"))
description := "Easy inspection of Scala macro expansions"
startYear := Some(2020)
licenses := Seq("MPLv2" → new URL("https://www.mozilla.org/en-US/MPL/2.0/"))
Compile / unmanagedResources += baseDirectory.value.getParentFile.getParentFile / "LICENSE"
scmInfo := Some(ScmInfo(url("https://github.com/sirthias/macrolizer/"), "scm:git:git@github.com:sirthias/macrolizer.git"))

scalaVersion := "3.1.2"
crossScalaVersions := List("3.1.2", "2.13.8")

libraryDependencies += ("org.scalameta"  %% "scalafmt-dynamic" % "3.5.4").cross(CrossVersion.for3Use2_13)
libraryDependencies ++= {
  if (CrossVersion.partialVersion(scalaVersion.value).get._1 == 2) {
    Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % Provided,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    )
  } else Nil
}

scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value).map(_._1) match {
    case Some(2) =>
      Seq(
        "-deprecation",
        "-encoding", "UTF-8",
        "-feature",
        "-unchecked",
        "-language:_",
        "-target:jvm-1.8",
        "-Xlint:_,-missing-interpolator",
        "-Ywarn-dead-code",
        "-Ywarn-numeric-widen",
        "-Ybackend-parallelism", "8",
        "-Ywarn-unused:imports,-patvars,-privates,-locals,-implicits,-explicits",
        "-Ycache-macro-class-loader:last-modified",
        "-Xfatal-warnings",
        "-Vimplicits",
        "-Vtype-diffs",
      )
    case Some(3) =>
      Seq(
        "-deprecation",
        "-encoding", "UTF-8",
        "-feature",
        "-unchecked",
        "-print-lines",
        "-source:future",
        "-pagewidth:120",
        "-Xtarget:11",
        "-Xfatal-warnings",
        "-Xcheck-macros",
      )
    case x => sys.error(s"unsupported scala version: $x")
  }
}
  
Compile / console / scalacOptions ~= (_ filterNot(o => o.contains("warn") || o.contains("Xlint")))
Test / console / scalacOptions := (Compile / console / scalacOptions).value
Compile / doc / scalacOptions += "-no-link-warnings"
sourcesInBase := false

// file headers
headerLicense := Some(HeaderLicense.MPLv2("2020 - 2022", "Mathias Doenitz"))

// reformat main and test sources on compile
scalafmtOnCompile := true

// publishing
publishMavenStyle := true
Test / publishArtifact := false
pomIncludeRepository := (_ ⇒ false)
publishTo := sonatypePublishToBundle.value

developers := List(
  Developer("sirthias", "Mathias Doenitz", "devnull@bullet.io", url("https://github.com/sirthias/"))
)

lazy val releaseSettings = {
  import ReleaseTransformations._
  Seq(
    releaseCrossBuild := true,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      releaseStepCommand("sonatypeBundleRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
}