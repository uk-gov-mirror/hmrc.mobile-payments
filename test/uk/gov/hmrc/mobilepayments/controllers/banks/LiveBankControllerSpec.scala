/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.mobilepayments.controllers.banks

import org.scalamock.handlers.CallHandler
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.{AuthConnector, ConfidenceLevel}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mobilepayments.MobilePaymentsTestData
import uk.gov.hmrc.mobilepayments.connectors.CitizenDetailsConnector
import uk.gov.hmrc.mobilepayments.domain.Shuttering
import uk.gov.hmrc.mobilepayments.domain.dto.response.BanksResponse
import uk.gov.hmrc.mobilepayments.domain.types.JourneyId
import uk.gov.hmrc.mobilepayments.mocks.{AuthorisationStub, ShutteringMock}
import uk.gov.hmrc.mobilepayments.services.{OpenBankingService, ShutteringService}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContext, Future}

class LiveBankControllerSpec extends AuthorisationStub with MobilePaymentsTestData with ShutteringMock {

  private val mockOpenBankingService: OpenBankingService = mock[OpenBankingService]
  private val sessionDataId: String = "51cc67d6-21da-11ec-9621-0242ac130002"

  implicit val mockShutteringService: ShutteringService = mock[ShutteringService]
  implicit val mockAuditConnector: AuditConnector = mock[AuditConnector]
  implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]

  private val sut = new LiveBankController(
    mockAuthConnector,
    mockCitizenDetailsConnector,
    ConfidenceLevel.L200.level,
    Helpers.stubControllerComponents(),
    mockOpenBankingService,
    mockShutteringService
  )

  "when get banks invoked and service returns success then" should {
    "return 200" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetBanks(Future successful BanksResponse(banksResponseGrouped))

      val request = FakeRequest("GET", "/banks")
        .withHeaders(acceptJsonHeader)

      val result = sut.getBanks(journeyId)(request)
      status(result) shouldBe 200
      val response = contentAsJson(result).as[BanksResponse]
      response.data.size shouldBe 9
    }
  }

  "when get banks invoked and service returns NotFoundException then" should {
    "return 404" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetBanks(Future failed UpstreamErrorResponse("Error", 404, 404))

      val request = FakeRequest("GET", "/banks")
        .withHeaders(acceptJsonHeader)

      val result = sut.getBanks(journeyId)(request)
      status(result) shouldBe 404
    }
  }

  "when get banks invoked and service returns 401 then" should {
    "return 401" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetBanks(Future failed UpstreamErrorResponse("Error", 401, 401))

      val request = FakeRequest("GET", "/banks")
        .withHeaders(acceptJsonHeader)

      val result = sut.getBanks(journeyId)(request)
      status(result) shouldBe 401
    }
  }

  "when get banks invoked and auth fails then" should {
    "return 401" in {
      stubAuthorisationWithAuthorisationException()

      val request = FakeRequest("GET", "/banks")
        .withHeaders(acceptJsonHeader)

      val result = sut.getBanks(journeyId)(request)
      status(result) shouldBe 401
    }
  }

  "when get banks invoked and service returns 5XX then" should {
    "return 500" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetBanks(Future failed UpstreamErrorResponse("Error", 502, 502))

      val request = FakeRequest("GET", "/banks")
        .withHeaders(acceptJsonHeader)

      val result = sut.getBanks(journeyId)(request)
      status(result) shouldBe 500
    }
  }

  "when select banks invoked and service returns success then" should {
    "return 201" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockSelectBank(Future successful ())

      val request = FakeRequest("POST", s"/banks/$sessionDataId")
        .withHeaders(acceptJsonHeader, contentHeader)
        .withBody(Json.obj("bankId" -> "12345"))

      val result = sut.selectBank(sessionDataId, journeyId)(request)
      status(result) shouldBe 201
    }
  }

  "when select banks invoked and service returns NotFoundException then" should {
    "return 404" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockSelectBank(Future failed UpstreamErrorResponse("Error", 404, 404))

      val request = FakeRequest("POST", s"/banks/$sessionDataId")
        .withHeaders(acceptJsonHeader, contentHeader)
        .withBody(Json.obj("bankId" -> "12345"))

      val result = sut.selectBank(sessionDataId, journeyId)(request)
      status(result) shouldBe 404
    }
  }

  "when select banks invoked and service returns 401 then" should {
    "return 401" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockSelectBank(Future failed UpstreamErrorResponse("Error", 401, 401))

      val request = FakeRequest("POST", s"/banks/$sessionDataId")
        .withHeaders(acceptJsonHeader, contentHeader)
        .withBody(Json.obj("bankId" -> "12345"))

      val result = sut.selectBank(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }
  }

  "when select banks invoked and auth fails then" should {
    "return 401" in {
      stubAuthorisationWithAuthorisationException()

      val request = FakeRequest("POST", s"/banks/$sessionDataId")
        .withHeaders(acceptJsonHeader, contentHeader)
        .withBody(Json.obj("bankId" -> "12345"))

      val result = sut.selectBank(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }
  }

  "when select banks invoked and service returns 5XX then" should {
    "return 500" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockSelectBank(Future failed UpstreamErrorResponse("Error", 502, 502))

      val request = FakeRequest("POST", s"/banks/$sessionDataId")
        .withHeaders(acceptJsonHeader, contentHeader)
        .withBody(Json.obj("bankId" -> "12345"))

      val result = sut.selectBank(sessionDataId, journeyId)(request)
      status(result) shouldBe 500
    }
  }

  private def mockGetBanks(f: Future[BanksResponse]) =
    (mockOpenBankingService
      .getBanks(_: JourneyId)(_: ExecutionContext, _: HeaderCarrier))
      .expects(*, *, *)
      .returning(f)

  private def mockSelectBank(f: Future[Unit]) =
    (mockOpenBankingService
      .selectBank(_: String, _: String, _: JourneyId)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *, *)
      .returning(f)

  private def shutteringDisabled(): CallHandler[Future[Shuttering]] =
    mockShutteringResponse(Shuttering(shuttered = false))
}
