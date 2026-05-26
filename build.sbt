import play.sbt.PlayImport.PlayKeys.playDefaultPort
import scoverage.ScoverageKeys

val appName = "mobile-payments"

lazy val scoverageSettings = {
  Seq(
    ScoverageKeys.coverageExcludedFiles := "<empty>;.*definition.*;prod.*;testOnlyDoNotUseInAppConf.*;app.*;.*BuildInfo.*;.*Routes.*;.*javascript.*;.*Reverse.*;.*Hooks.*;.*AppConfig.*;.*AppConfig.*;.*mobilepayments.models.*;.*mobilepayments.config.*;.*mobilepayments.domain.*;.*mobilepayments.controllers.api.*",
    coverageMinimumStmtTotal := 90, // Need to increase this later
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(
    Seq(
      play.sbt.PlayScala,
      SbtDistributablesPlugin,
      ScoverageSbtPlugin
    ): _*
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    routesImport ++= Seq(
      "uk.gov.hmrc.mobilepayments.domain.types._",
      "uk.gov.hmrc.mobilepayments.domain.types.JourneyId._"
    )
  )
  .settings(
    majorVersion := 0,
    scalaVersion := "3.6.4",
    playDefaultPort := 8262,
    libraryDependencies ++= AppDependencies(),
    update / evictionWarningOptions := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    IntegrationTest / unmanagedSourceDirectories := (IntegrationTest / baseDirectory)(base => Seq(base / "it", base / "test-common")).value,
    Test / unmanagedSourceDirectories := (Test / baseDirectory)(base => Seq(base / "test", base / "test-common")).value,
    IntegrationTest / parallelExecution := false,
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-language:higherKinds",
      "-language:postfixOps",
      "-feature",
      "-Ywarn-dead-code",
      "-Ywarn-value-discard",
      "-Ywarn-numeric-widen",
      "-Xlint"
    )
  )
  .settings(scoverageSettings: _*)
  .settings(
    scalacOptions ++= Seq(
      // Suppress warnings matching specific message pattern
      "-Wconf:msg=possible missing interpolator.*\\$date:silent",
      // Suppress warnings in generated files (e.g., target directory)
      "-Wconf:src=target/.*:silent",
      // Default rule to warn on everything else
      "-Wconf:any:warning"
    )
  )
