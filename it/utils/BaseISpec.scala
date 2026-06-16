/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.WsScalaTestClient
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.time.TaxYear

abstract class BaseISpec
    extends AnyWordSpecLike
    with Matchers
    with WsScalaTestClient
    with GuiceOneServerPerSuite
    with WireMockSupport
    with FutureAwaits
    with DefaultAwaitTimeout {

  override implicit lazy val app: Application = appBuilder.build()
  protected val sandboxHeader: (String, String) = "X-MOBILE-USER-ID"        -> "208606423740"
  protected val contentHeader: (String, String) = "Content-Type"            -> "application/json"
  protected val acceptJsonHeader: (String, String) = "Accept"               -> "application/vnd.hmrc.1.0+json"
  protected val authorisationJsonHeader: (String, String) = "AUTHORIZATION" -> "Bearer 123"
  protected val sessionIdHeader: (String, String) = "X-Session-ID"          -> "13345a9d-0958-4931-ae83-5a36e4ccd979"
  val journeyId: String = "27085215-69a4-4027-8f72-b04b10ec16b0"
  val taxYear = TaxYear.current.previous.startYear

  def config: Map[String, Any] =
    Map[String, Any](
      "auditing.enabled"                             -> false,
      "microservice.services.auth.port"              -> wireMockPort,
      "microservice.services.open-banking.port"      -> wireMockPort,
      "microservice.services.payments.port"          -> wireMockPort,
      "microservice.services.mobile-shuttering.port" -> wireMockPort,
      "microservice.services.p800-payments.port"     -> wireMockPort,
      "microservice.services.citizen-details.port"   -> wireMockPort
    )

  protected def appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder().configure(config)

  protected implicit lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
}
