import org.typelevel.Dependencies._

/**
 * These aliases serialise the build for the benefit of Travis-CI, also useful for pre-PR testing.
 * If new projects are added to the build, these must be updated.
 */
addCommandAlias("buildJVM",    ";macrosJVM/compile;platformJVM/compile;scalatestJVM/test;specs2/test;testkitJVM/compile;testsJVM/test")
addCommandAlias("validateJVM", ";scalastyle;buildJVM")
addCommandAlias("validateJS",  ";macrosJS/compile;platformJS/compile;scalatestJS/test;testkitJS/compile;testsJS/test")
addCommandAlias("validate",    ";validateJS;validateJVM")
addCommandAlias("validateAll", s""";++${vers("scalac")};+clean;+validate;++${vers("scalac")};docs/makeSite""") 
addCommandAlias("gitSnapshots", ";set version in ThisBuild := git.gitDescribedVersion.value.get + \"-SNAPSHOT\"")

/**
 * Project settings
 */
val gh = GitHubSettings(org = "InTheNow", proj = "catalysts", publishOrg = "org.typelevel", license = apache)
val devs = Seq(Dev("Alistair Johnson", "inthenow"))

val vers = versions // to add/overide versions: val vers = versions + ("discipline" -> "0.3")
val libs = libraries
val addins = scalacPlugins
val vAll = Versions(vers, libs, addins)

/**
 * catalysts - This is the root project that aggregates the catalystsJVM and catalystsJS sub projects
 */
lazy val rootSettings = buildSettings ++ commonSettings ++ publishSettings ++ scoverageSettings

lazy val module = mkModuleFactory(gh.proj, mkConfig(rootSettings, commonJvmSettings, commonJsSettings))
lazy val prj = mkPrjFactory(rootSettings)

lazy val rootPrj = project
  .configure(mkRootConfig(rootSettings,rootJVM))
  .aggregate(rootJVM, rootJS)
  .dependsOn(rootJVM, rootJS, testsJVM % "test-internal -> test")

lazy val rootJVM = project
  .configure(mkRootJvmConfig(gh.proj, rootSettings, commonJvmSettings))
  .aggregate(checkliteJVM, macrosJVM, platformJVM, scalatestJVM, specs2, specliteJVM, testkitJVM, testsJVM, docs)
  .dependsOn(checkliteJVM, macrosJVM, platformJVM, scalatestJVM, specs2, specliteJVM, testkitJVM, testsJVM % "compile;test-internal -> test")

lazy val rootJS = project
  .configure(mkRootJsConfig(gh.proj, rootSettings, commonJsSettings))
  .aggregate(checkliteJS, macrosJS, platformJS, scalatestJS, specliteJS, testkitJS, testsJS)

/**
 * CheckLite - cross project that implements a basic test framework, based on ScalaCheck.
 */
lazy val checklite    = prj(checkliteM)
lazy val checkliteJVM = checkliteM.jvm
lazy val checkliteJS  = checkliteM.js
lazy val checkliteM   = module("checklite", CrossType.Pure)
  .dependsOn(testkitM % "compile; test -> test", lawkitM % "compile; test -> test")
  .settings(disciplineDependencies:_*)
  .settings(addLibs(vAll, "scalacheck"):_*)
  .jvmSettings(libraryDependencies += "org.scala-sbt" %  "test-interface" % "1.0")
  .jvmSettings(libraryDependencies += "org.scala-js" %% "scalajs-stubs" % scalaJSVersion) // % "provided",
  .jsSettings( libraryDependencies += "org.scala-js" %% "scalajs-test-interface" % scalaJSVersion)

/**
 * Macros - cross project that defines macros
 */
lazy val macros    = prj(macrosM)
lazy val macrosJVM = macrosM.jvm
lazy val macrosJS  = macrosM.js
lazy val macrosM   = module("macros", CrossType.Pure)
  .settings(macroCompatSettings(vAll):_*)

/**
 * Platform - cross project that provides cross platform support
 */
lazy val platform    = prj(platformM)
lazy val platformJVM = platformM.jvm
lazy val platformJS  = platformM.js
lazy val platformM   = module("platform", CrossType.Dummy)
  .dependsOn(macrosM)

/**
 * Scalatest - cross project that defines test utilities for scalatest
 */
lazy val scalatest    = prj(scalatestM)
lazy val scalatestJVM = scalatestM.jvm
lazy val scalatestJS  = scalatestM.js
lazy val scalatestM   = module("scalatest", CrossType.Pure)
  .dependsOn(testkitM % "compile; test -> test", lawkitM % "compile; test -> test")
  .settings(disciplineDependencies:_*)
  .settings(addLibs(vAll, "scalatest"):_*)

/**
 * Specs2 - JVM project that defines test utilities for specs2
 */
lazy val specs2 = project
  .dependsOn(testkitJVM % "compile; test -> test", lawkitJVM % "compile; test -> test")
  .settings(moduleName := "catalysts-specs2")
  .settings(rootSettings:_*)
  .settings(commonJvmSettings:_*)
  .settings(disciplineDependencies:_*)
  .settings(addLibs(vAll, "specs2-core","specs2-scalacheck" ):_*)

/**
 * Lawkit - cross project that add Law and Property checking ti TestKit
 */
lazy val lawkit    = prj(lawkitM)
lazy val lawkitJVM = lawkitM.jvm
lazy val lawkitJS  = lawkitM.js
lazy val lawkitM   = module("lawkit", CrossType.Pure)
  .dependsOn(macrosM, testkitM)
  .settings(macroCompatSettings(vAll):_*)
  .settings(disciplineDependencies:_*)

/**
 * Testkit - cross project that defines test utilities that can be re-used in other libraries, as well as 
 *         all the tests for this build.
 */
lazy val testkit    = prj(testkitM)
lazy val testkitJVM = testkitM.jvm
lazy val testkitJS  = testkitM.js
lazy val testkitM   = module("testkit", CrossType.Pure)
  .dependsOn(macrosM, platformM)
  .settings(macroCompatSettings(vAll):_*)

/**
 * Speclite - cross project that implements a basic test framework, with minimal external dependencies.
 */
lazy val speclite    = prj(specliteM)
lazy val specliteJVM = specliteM.jvm
lazy val specliteJS  = specliteM.js
lazy val specliteM   =  module("speclite", CrossType.Pure)
  .dependsOn(platformM, testkitM % "compile; test -> test")
  .settings(testFrameworks := Seq(new TestFramework("catalysts.speclite.SpecLiteFramework")))
  .jvmSettings(libraryDependencies += "org.scala-sbt" %  "test-interface" % "1.0")
  .jvmSettings(libraryDependencies += "org.scala-js" %% "scalajs-stubs" % scalaJSVersion) // % "provided", 
  .jsSettings( libraryDependencies += "org.scala-js" %% "scalajs-test-interface" % scalaJSVersion)

/*
 * Tests - cross project that defines test utilities that can be re-used in other libraries, as well as 
 *         all the tests for this build.
 */
lazy val tests    = prj(testsM)
lazy val testsJVM = testsM.jvm
lazy val testsJS  = testsM.js
lazy val testsM   = module("tests", CrossType.Pure)
  .dependsOn(macrosM, platformM, testkitM, specliteM % "test-internal -> test", scalatestM % "test-internal -> test")
  .settings(disciplineDependencies:_*)
  .settings(noPublishSettings:_*)
  .settings(addTestLibs(vAll, "scalatest" ):_*)
  .settings(testFrameworks ++= Seq(new TestFramework("catalysts.speclite.SpecLiteFramework")))

/**
 * Docs - Generates and publishes the scaladoc API documents and the project web site 
 */
lazy val docs = project.configure(mkDocConfig(gh, rootSettings, commonJvmSettings,
    platformJVM, macrosJVM, scalatestJVM, specs2, testkitJVM ))

/**
 * Settings
 */
lazy val buildSettings = sharedBuildSettings(gh, vAll)

lazy val commonSettings = sharedCommonSettings ++ Seq(
  scalacOptions ++= scalacAllOptions,
  parallelExecution in Test := false
) ++ warnUnusedImport ++ unidocCommonSettings

lazy val commonJsSettings = Seq(
  scalaJSStage in Global := FastOptStage
)

lazy val commonJvmSettings = Seq()

lazy val disciplineDependencies = Seq(addLibs(vAll, "discipline", "scalacheck" ):_*)

lazy val publishSettings = sharedPublishSettings(gh, devs) ++ credentialSettings ++ sharedReleaseProcess

lazy val scoverageSettings = sharedScoverageSettings(60)
