import sbt.*

object AppDependencies {

  private val bootstrapPlayVersion = "10.7.0"
  private val playHmrcApiVersion = "9.0.0"
  private val refinedVersion = "0.11.3"
  private val domainVersion = "13.0.0"

  private val scalaMockVersion = "7.5.5"
  private val pekkoHttpVersion = "1.2.0" // replaced akka with pekko and upgraded mixing version messing up with test cases
  private val pekkoActorVersion = "1.0.3" // keep these two versions same until some compatible version come up

  val compile = Seq(
    "uk.gov.hmrc"      %% "play-hmrc-api-play-30" % playHmrcApiVersion,
    "eu.timepit"       %% "refined"               % refinedVersion,
    "uk.gov.hmrc"      %% "domain-play-30"        % domainVersion,
    "com.beachape"     %% "enumeratum"            % "1.9.7",
    "org.typelevel"    %% "cats-core"             % "2.13.0",
    "org.apache.pekko" %% "pekko-http"            % pekkoHttpVersion,
    "org.apache.pekko" %% "pekko-actor"           % pekkoActorVersion
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {

    def apply(): Seq[ModuleID] =
      new TestDependencies {

        override lazy val test: Seq[ModuleID] = testCommon(scope) ++ Seq(
          "org.scalamock" %% "scalamock" % scalaMockVersion % scope
        )
      }.test
  }

  object IntegrationTest {

    def apply(): Seq[ModuleID] =
      new TestDependencies {

        override lazy val scope: String = "it"

        override lazy val test: Seq[ModuleID] = testCommon(scope)
      }.test
  }

  private def testCommon(scope: String) = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapPlayVersion % scope
  )

  def apply(): Seq[ModuleID] = compile ++ Test() ++ IntegrationTest()
}
