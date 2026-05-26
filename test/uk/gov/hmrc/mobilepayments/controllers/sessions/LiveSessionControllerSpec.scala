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

package uk.gov.hmrc.mobilepayments.controllers.sessions

import org.scalamock.handlers.CallHandler
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.{AuthConnector, ConfidenceLevel}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mobilepayments.MobilePaymentsTestData
import uk.gov.hmrc.mobilepayments.common.BaseSpec
import uk.gov.hmrc.mobilepayments.domain.Shuttering
import uk.gov.hmrc.mobilepayments.domain.dto.request.CreateSessionRequest
import uk.gov.hmrc.mobilepayments.domain.dto.response.SessionDataResponse
import uk.gov.hmrc.mobilepayments.domain.types.JourneyId
import uk.gov.hmrc.mobilepayments.mocks.{AuthorisationStub, ShutteringMock}
import uk.gov.hmrc.mobilepayments.models.openBanking.response.CreateSessionDataResponse
import uk.gov.hmrc.mobilepayments.services.{AuditService, OpenBankingService, ShutteringService}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class LiveSessionControllerSpec extends BaseSpec with AuthorisationStub with MobilePaymentsTestData with ShutteringMock {

  private val mockOpenBankingService: OpenBankingService = mock[OpenBankingService]
  private val sessionDataId: String = "51cc67d6-21da-11ec-9621-0242ac130002"

  implicit val mockShutteringService: ShutteringService = mock[ShutteringService]
  implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit val mockAuditService: AuditService = mock[AuditService]

  private val sut = new LiveSessionController(
    mockAuthConnector,
    ConfidenceLevel.L200.level,
    Helpers.stubControllerComponents(),
    mockOpenBankingService,
    mockShutteringService
  )

  "when create session invoked and service returns success then" should {
    "return 200" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      stubGetUTRFromAuth(utrAndEnrolmentResponse)
      mockCreateSession(Future successful createSessionDataResponse)

      val request = FakeRequest("POST", "/sessions")
        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
        .withBody(Json.obj("amount" -> 1234, "reference" -> "12212321"))

      val result = sut.createSession(journeyId)(request)
      status(result) shouldBe 200
      val response = contentAsJson(result).as[CreateSessionDataResponse]
      response.sessionDataId.value shouldEqual sessionDataId
    }
  }

  // "Calling create session with taxType as SimpleAssessment, reference and amountInPence" should {
//    "return 200" in {
//      stubAuthorisationGrantAccess(authorisedResponse)
//      shutteringDisabled()
//      stubGetUTRFromAuth(utrAndEnrolmentResponse)
//      mockCreateSession(Future successful createSessionDataResponse)
//
//      val request = FakeRequest("POST", "/sessions")
//        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
//        .withBody(Json.obj("amountInPence" -> 1234, "reference" -> "12212321", "taxType" -> "appSimpleAssessment"))
//
//      val result = sut.createSession(journeyId)(request)
//      status(result) shouldBe 200
//      val response = contentAsJson(result).as[CreateSessionDataResponse]
//      response.sessionDataId.value shouldEqual sessionDataId
//    }
  // }

  "Calling create session with taxType as SelfAssessment, reference and amountInPence" should {
    "return 200" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      stubGetUTRFromAuth(utrAndEnrolmentResponse)
      mockCreateSession(Future successful createSessionDataResponse)

      val request = FakeRequest("POST", "/sessions")
        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
        .withBody(Json.obj("amountInPence" -> 1234, "reference" -> "12212321", "taxType" -> "appSelfAssessment"))

      val result = sut.createSession(journeyId)(request)
      status(result) shouldBe 200
      val response = contentAsJson(result).as[CreateSessionDataResponse]
      response.sessionDataId.value shouldEqual sessionDataId
    }
  }

  "when create session invoked with malformed json then" should {
    "return 400" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      stubGetUTRFromAuth(utrAndEnrolmentResponse)
      mockCreateSession(Future failed UpstreamErrorResponse("Error", 400, 400))
      val request = FakeRequest("POST", "/sessions")
        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
        .withBody(Json.obj("bad-key" -> 1234, "saUtr" -> "12212321"))

      val result = sut.createSession(journeyId)(request)
      status(result) shouldBe 400
    }
  }

  "when create session invoked and service returns 401 then" should {
    "return 401" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      stubGetUTRFromAuth(utrAndEnrolmentResponse)
      mockCreateSession(Future failed UpstreamErrorResponse("Error", 401, 401))

      val request = FakeRequest("POST", "/sessions")
        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
        .withBody(Json.obj("amount" -> 1234, "saUtr" -> "12212321"))

      val result = sut.createSession(journeyId)(request)
      status(result) shouldBe 401
    }
  }

  "when create session invoked and auth fails then" should {
    "return 401" in {
      stubAuthorisationWithAuthorisationException()

      val request = FakeRequest("POST", "/sessions")
        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
        .withBody(Json.obj("amount" -> 1234, "saUtr" -> "CS700100A"))

      val result = sut.createSession(journeyId)(request)
      status(result) shouldBe 401
    }
  }

  "when create session invoked and service returns 5XX then" should {
    "return 500" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      stubGetUTRFromAuth(utrAndEnrolmentResponse)
      mockCreateSession(Future failed UpstreamErrorResponse("Error", 502, 502))

      val request = FakeRequest("POST", "/sessions")
        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
        .withBody(Json.obj("amount" -> 1234, "saUtr" -> "12212321"))

      val result = sut.createSession(journeyId)(request)
      status(result) shouldBe 500
    }
  }

  "when get session invoked for bank selected state and service returns success then" should {
    "return 200" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()

      mockGetSession(Future successful sessionDataResponse)

      val request = FakeRequest("Get", s"/sessions/$sessionDataId")
        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

      val result = sut.getSession(sessionDataId, journeyId)(request)
      status(result) shouldBe 200
      val response = contentAsJson(result).as[SessionDataResponse]
      response.sessionDataId shouldEqual sessionDataId
      response.amountInPence shouldEqual 12564
      response.bankId        shouldEqual Some("some-bank-id")
      response.paymentDate   shouldEqual None
      response.reference     shouldEqual "CS700100AK"
    }
  }

  "when get session invoked for payment finalised state and service returns success then" should {
    "return 200" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetSession(Future successful sessionDataPaymentFinalisedResponse)

      val request = FakeRequest("Get", s"/sessions/$sessionDataId")
        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

      val result = sut.getSession(sessionDataId, journeyId)(request)
      status(result) shouldBe 200
      val response = contentAsJson(result).as[SessionDataResponse]
      response.sessionDataId shouldEqual sessionDataId
      response.amountInPence shouldEqual 12564
      response.bankId        shouldEqual Some("some-bank-id")
      response.paymentDate   shouldEqual Some(LocalDate.parse("2021-12-01"))
      response.reference     shouldEqual "CS700100AK"
      response.email.get     shouldEqual "test@test.com"
    }
  }

  "when get session invoked and service returns 401 then" should {
    "return 401" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetSession(Future failed UpstreamErrorResponse("Error", 401, 401))

      val request = FakeRequest("Get", s"/sessions/$sessionDataId")
        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

      val result = sut.getSession(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }
  }

  "when get session invoked and auth fails then" should {
    "return 401" in {
      stubAuthorisationWithAuthorisationException()

      val request = FakeRequest("Get", s"/sessions/$sessionDataId")
        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

      val result = sut.getSession(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }
  }

  "when get session invoked and service returns 5XX then" should {
    "return 500" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetSession(Future failed UpstreamErrorResponse("Error", 502, 502))

      val request = FakeRequest("Get", s"/sessions/$sessionDataId")
        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

      val result = sut.getSession(sessionDataId, journeyId)(request)
      status(result) shouldBe 500
    }
  }

  "when set email invoked and service returns success then" should {
    "return 201" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockSetEmail(Future successful ())

      val request = FakeRequest("POST", s"/sessions/$sessionDataId/set-email")
        .withHeaders(acceptJsonHeader, contentHeader)
        .withBody(Json.obj("email" -> "test@test.com"))

      val result = sut.setEmail(sessionDataId, journeyId)(request)
      status(result) shouldBe 201
    }
  }

  "when set email invoked and service returns NotFoundException then" should {
    "return 404" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockSetEmail(Future failed UpstreamErrorResponse("Error", 404, 404))

      val request = FakeRequest("POST", s"/sessions/$sessionDataId/set-email")
        .withHeaders(acceptJsonHeader, contentHeader)
        .withBody(Json.obj("email" -> "test@test.com"))

      val result = sut.setEmail(sessionDataId, journeyId)(request)
      status(result) shouldBe 404
    }
  }

  "when set email invoked and service returns 401 then" should {
    "return 401" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockSetEmail(Future failed UpstreamErrorResponse("Error", 401, 401))

      val request = FakeRequest("POST", s"/sessions/$sessionDataId/set-email")
        .withHeaders(acceptJsonHeader, contentHeader)
        .withBody(Json.obj("email" -> "test@test.com"))

      val result = sut.setEmail(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }
  }

  "when set email invoked and auth fails then" should {
    "return 401" in {
      stubAuthorisationWithAuthorisationException()

      val request = FakeRequest("POST", s"/sessions/$sessionDataId/set-email")
        .withHeaders(acceptJsonHeader, contentHeader)
        .withBody(Json.obj("email" -> "test@test.com"))

      val result = sut.setEmail(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }
  }

  "when set email invoked and service returns 5XX then" should {
    "return 500" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockSetEmail(Future failed UpstreamErrorResponse("Error", 502, 502))

      val request = FakeRequest("POST", s"/sessions/$sessionDataId/set-email")
        .withHeaders(acceptJsonHeader, contentHeader)
        .withBody(Json.obj("email" -> "test@test.com"))

      val result = sut.setEmail(sessionDataId, journeyId)(request)
      status(result) shouldBe 500
    }
  }

  "when clear email invoked and service returns success then" should {
    "return 204" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockClearEmail(Future successful ())

      val request =
        FakeRequest("DELETE", s"/sessions/$sessionDataId/clear-email").withHeaders(acceptJsonHeader)

      val result = sut.clearEmail(sessionDataId, journeyId)(request)
      status(result) shouldBe 204
    }
  }

  "when clear email invoked and service returns NotFoundException then" should {
    "return 404" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockClearEmail(Future failed UpstreamErrorResponse("Error", 404, 404))

      val request = FakeRequest("DELETE", s"/sessions/$sessionDataId/clear-email")
        .withHeaders(acceptJsonHeader)

      val result = sut.clearEmail(sessionDataId, journeyId)(request)
      status(result) shouldBe 404
    }
  }

  "when clear email invoked and service returns 401 then" should {
    "return 401" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockClearEmail(Future failed UpstreamErrorResponse("Error", 401, 401))

      val request = FakeRequest("DELETE", s"/sessions/$sessionDataId/clear-email")
        .withHeaders(acceptJsonHeader)

      val result = sut.clearEmail(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }
  }

  "when clear email invoked and auth fails then" should {
    "return 401" in {
      stubAuthorisationWithAuthorisationException()

      val request = FakeRequest("DELETE", s"/sessions/$sessionDataId/clear-email")
        .withHeaders(acceptJsonHeader)

      val result = sut.clearEmail(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }
  }

  "when clear email invoked and service returns 5XX then" should {
    "return 500" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockClearEmail(Future failed UpstreamErrorResponse("Error", 502, 502))

      val request = FakeRequest("DELETE", s"/sessions/$sessionDataId/clear-email")
        .withHeaders(acceptJsonHeader)

      val result = sut.clearEmail(sessionDataId, journeyId)(request)
      status(result) shouldBe 500
    }
  }

  private def mockClearEmail(f: Future[Unit]) =
    (mockOpenBankingService
      .clearEmail(_: String, _: JourneyId)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(f)

  private def mockSetEmail(f: Future[Unit]) =
    (mockOpenBankingService
      .setEmail(_: String, _: String, _: JourneyId)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *, *)
      .returning(f)

  private def mockCreateSession(future: Future[CreateSessionDataResponse]) =
    (mockOpenBankingService
      .createSession(_: CreateSessionRequest, _: JourneyId)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(future)

  private def mockGetSession(future: Future[SessionDataResponse]) =
    (mockOpenBankingService
      .getSession(_: String, _: JourneyId)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(future)

  private def shutteringDisabled(): CallHandler[Future[Shuttering]] =
    mockShutteringResponse(Shuttering(shuttered = false))
}
