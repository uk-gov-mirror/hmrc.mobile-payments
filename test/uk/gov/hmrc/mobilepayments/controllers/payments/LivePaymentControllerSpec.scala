/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.mobilepayments.controllers.payments

import org.scalamock.handlers.CallHandler
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.{AuthConnector, ConfidenceLevel}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mobilepayments.MobilePaymentsTestData
import uk.gov.hmrc.mobilepayments.common.BaseSpec
import uk.gov.hmrc.mobilepayments.connectors.CitizenDetailsConnector
import uk.gov.hmrc.mobilepayments.domain.Shuttering
import uk.gov.hmrc.mobilepayments.domain.dto.request.PayByCardRequestGeneric
import uk.gov.hmrc.mobilepayments.domain.dto.request.TaxTypeEnum
import uk.gov.hmrc.mobilepayments.domain.dto.response.{LatestPaymentsResponse, Origin, PayByCardResponse, PaymentStatusResponse, SessionDataResponse, UrlConsumedResponse}
import uk.gov.hmrc.mobilepayments.domain.types.JourneyId
import uk.gov.hmrc.mobilepayments.mocks.{AuthorisationStub, ShutteringMock}
import uk.gov.hmrc.mobilepayments.models.openBanking.response.InitiatePaymentResponse
import uk.gov.hmrc.mobilepayments.services.{AuditService, OpenBankingService, PaymentsService, ShutteringService}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import scala.concurrent.{ExecutionContext, Future}

class LivePaymentControllerSpec extends BaseSpec with AuthorisationStub with MobilePaymentsTestData with ShutteringMock {

  private val mockOpenBankingService: OpenBankingService = mock[OpenBankingService]
  private val sessionDataId: String = "51cc67d6-21da-11ec-9621-0242ac130002"
  private val utr: String = "12212321"
  private val nino: String = "CS700100A"

  implicit val mockShutteringService: ShutteringService = mock[ShutteringService]
  implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]
  implicit val mockAuditService: AuditService = mock[AuditService]
  implicit val mockPaymentsService: PaymentsService = mock[PaymentsService]

  private val sut = new LivePaymentController(
    mockAuthConnector,
    mockCitizenDetailsConnector,
    ConfidenceLevel.L200.level,
    Helpers.stubControllerComponents(),
    mockOpenBankingService,
    mockShutteringService,
    mockAuditService,
    mockPaymentsService
  )
  "create Payment " should {

    "return 200 when service returns success" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockInitiatePayment(Future successful paymentSessionResponse)
      mockGetSession(Future successful sessionDataResponse)
      stubPaymentEvent()

      val request = FakeRequest("POST", s"/payments/$sessionDataId")
        .withHeaders(acceptJsonHeader)

      val result = sut.createPayment(sessionDataId, journeyId)(request)
      status(result) shouldBe 200
      val response = contentAsJson(result).as[InitiatePaymentResponse]
      response.paymentUrl.toString() shouldEqual "https://some-bank.com?param=dosomething"

    }

    "return 401, when payment service returns 401 " in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockInitiatePayment(Future failed UpstreamErrorResponse("Error", 401, 401))

      val request = FakeRequest("POST", s"/payments/$sessionDataId")
        .withHeaders(acceptJsonHeader)

      val result = sut.createPayment(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }

    "return 401, when auth fails" in {
      stubAuthorisationWithAuthorisationException()

      val request = FakeRequest("POST", s"/payments/$sessionDataId")
        .withHeaders(acceptJsonHeader)

      val result = sut.createPayment(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }

    "return 500, when payment service returns 5XX " in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockInitiatePayment(Future failed UpstreamErrorResponse("Error", 502, 502))

      val request = FakeRequest("POST", s"/payments/$sessionDataId")
        .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

      val result = sut.createPayment(sessionDataId, journeyId)(request)
      status(result) shouldBe 500
    }
  }

  "Update Payment" should {

    "return 200 when service returns success" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockUpdatePayment(Future successful paymentSessionResponse)

      val request = FakeRequest("PUT", s"/payments/$sessionDataId")
        .withHeaders(acceptJsonHeader, contentHeader)

      val result = sut.updatePayment(sessionDataId, journeyId)(request)
      status(result) shouldBe 200
      val response = contentAsJson(result).as[InitiatePaymentResponse]
      response.paymentUrl.toString() shouldEqual "https://some-bank.com?param=dosomething"
    }

    "return 401, when service returns 401" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockUpdatePayment(Future failed UpstreamErrorResponse("Error", 401, 401))

      val request = FakeRequest("PUT", s"/payments/$sessionDataId")
        .withHeaders(acceptJsonHeader, contentHeader)

      val result = sut.updatePayment(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }

    "return 401, when auth fails" in {
      stubAuthorisationWithAuthorisationException()

      val request = FakeRequest("PUT", s"/payments/$sessionDataId")
        .withHeaders(acceptJsonHeader, contentHeader)

      val result = sut.updatePayment(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }

    "return 500, when service returns 5XX " in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockUpdatePayment(Future failed UpstreamErrorResponse("Error", 502, 502))

      val request = FakeRequest("PUT", s"/payments/$sessionDataId")
        .withHeaders(acceptJsonHeader, contentHeader)

      val result = sut.updatePayment(sessionDataId, journeyId)(request)
      status(result) shouldBe 500
    }
  }

  "urlConsumed" should {

    "return 200, if service returns success" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetUrlConsumed(Future successful UrlConsumedResponse(true))

      val request = FakeRequest("GET", s"/payments/$sessionDataId/url-consumed")
        .withHeaders(acceptJsonHeader)

      val result = sut.urlConsumed(sessionDataId, journeyId)(request)
      status(result) shouldBe 200
      val response = contentAsJson(result).as[UrlConsumedResponse]
      response.consumed shouldBe true
    }

    "return 404, if service returns NotFoundException" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetUrlConsumed(Future.failed(UpstreamErrorResponse("Error", 404, 404)))

      val request = FakeRequest("GET", s"/payments/$sessionDataId/url-consumed")
        .withHeaders(acceptJsonHeader)

      val result = sut.urlConsumed(sessionDataId, journeyId)(request)
      status(result) shouldBe 404
    }

    "return 401, if service returns 401" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetUrlConsumed(Future.failed(UpstreamErrorResponse("Error", 401, 401)))

      val request = FakeRequest("GET", s"/payments/$sessionDataId/url-consumed")
        .withHeaders(acceptJsonHeader)

      val result = sut.urlConsumed(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }

    "return 401, if auth  fails" in {
      stubAuthorisationWithAuthorisationException()

      val request = FakeRequest("GET", s"/payments/$sessionDataId/url-consumed")
        .withHeaders(acceptJsonHeader)

      val result = sut.urlConsumed(sessionDataId, journeyId)(request)
      status(result) shouldBe 401
    }

    "return 500, if service returns 5XX" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetUrlConsumed(Future failed UpstreamErrorResponse("Error", 502, 502))

      val request = FakeRequest("GET", s"/payments/$sessionDataId/url-consumed")
        .withHeaders(acceptJsonHeader)

      val result = sut.urlConsumed(sessionDataId, journeyId)(request)
      status(result) shouldBe 500
    }
  }

  "Get Payment Status" should {

    "return 200" when {

      "trigger sending of email if status Verified or Complete" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        mockGetPaymentStatus(Future successful PaymentStatusResponse("Verified"))
        mockGetSession(Future successful sessionDataResponse)
        mockSendEmail()

        val request = FakeRequest("GET", s"/payments/$sessionDataId")
          .withHeaders(acceptJsonHeader)

        val result = sut.getPaymentStatus(sessionDataId, journeyId)(request)
        status(result) shouldBe 200
        val response = contentAsJson(result).as[PaymentStatusResponse]
        response.status shouldEqual "Verified"
      }

      " trigger sending of email if status is not Verified or Complete" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        mockGetPaymentStatus(Future successful PaymentStatusResponse("Authorised"))
        mockGetSession(Future successful sessionDataResponse)

        val request = FakeRequest("GET", s"/payments/$sessionDataId")
          .withHeaders(acceptJsonHeader)

        val result = sut.getPaymentStatus(sessionDataId, journeyId)(request)
        status(result) shouldBe 200
        val response = contentAsJson(result).as[PaymentStatusResponse]
        response.status shouldEqual "Authorised"
      }
    }

    "return 404, when payment service returns NotFoundException" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetPaymentStatus(Future.failed(UpstreamErrorResponse("Error", 404, 404)))

      val request = FakeRequest("GET", s"/payments/$sessionDataId")
        .withHeaders(acceptJsonHeader)

      val result = sut.getPaymentStatus(sessionDataId, journeyId)(request)
      status(result) shouldBe 404
    }

    "return 401 " when {
      "when service returns 401" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        mockGetPaymentStatus(Future.failed(UpstreamErrorResponse("Error", 401, 401)))

        val request = FakeRequest("GET", s"/payments/$sessionDataId")
          .withHeaders(acceptJsonHeader)

        val result = sut.getPaymentStatus(sessionDataId, journeyId)(request)
        status(result) shouldBe 401
      }

      "auth fails" in {
        stubAuthorisationWithAuthorisationException()

        val request = FakeRequest("GET", s"/payments/$sessionDataId")
          .withHeaders(acceptJsonHeader)

        val result = sut.getPaymentStatus(sessionDataId, journeyId)(request)
        status(result) shouldBe 401
      }

    }

    "return 500, when  service returns 5XX" in {
      stubAuthorisationGrantAccess(authorisedResponse)
      shutteringDisabled()
      mockGetPaymentStatus(Future.failed(UpstreamErrorResponse("Error", 502, 502)))

      val request = FakeRequest("GET", s"/payments/$sessionDataId")
        .withHeaders(acceptJsonHeader)

      val result = sut.getPaymentStatus(sessionDataId, journeyId)(request)
      status(result) shouldBe 500
    }
  }

  "latestPayments" when {

    "taxType = appSelfAssessment " should {

      "return latest Payment if payload reference == UTR , IR-SA enrolment is there" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), Some(utr), saOnlyEnrolments))
        mockGetLatestPayments(Future successful Right(Some(latestPaymentsResponse)))

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> "appSelfAssessment", "reference" -> utr))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 200
        val response = contentAsJson(result).as[LatestPaymentsResponse]
        response.payments.size               shouldBe 2
        response.payments.head.amountInPence shouldBe 11100
        response.payments.head.date.toString shouldBe "2022-05-07"
      }

      "return latest Payment if payload reference == UTR , IR-SA and HMRC-MTD-IT saOnlyEnrolments are there" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), Some(utr), saMTDEnrolments))
        mockGetLatestPayments(Future successful Right(Some(latestPaymentsResponse)))

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> "appSelfAssessment", "reference" -> utr))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 200
        val response = contentAsJson(result).as[LatestPaymentsResponse]
        response.payments.size               shouldBe 2
        response.payments.head.amountInPence shouldBe 11100
        response.payments.head.date.toString shouldBe "2022-05-07"
      }

      "return latest Payment if payload reference == UTR , Only HMRC-MTD-IT enrolment is there" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), None, mtdOnlyEnrolments))
        stubGetUTRByNino(Future.successful(Some(SaUtr(utr))))
        mockGetLatestPayments(Future successful Right(Some(latestPaymentsResponse)))

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> "appSelfAssessment", "reference" -> utr))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 200
        val response = contentAsJson(result).as[LatestPaymentsResponse]
        response.payments.size               shouldBe 2
        response.payments.head.amountInPence shouldBe 11100
        response.payments.head.date.toString shouldBe "2022-05-07"
      }

      "return 401, if reference !== UTR" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()

        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), Some(utr), saOnlyEnrolments))
        mockGetLatestPayments(Future successful Left("Unauthorized! Reference in payload doesn't match with logged in UTR"))

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> "appSelfAssessment", "reference" -> "1234567"))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 401
      }

      "return 401, if UTR is not fetched from Auth" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()

        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), None, saOnlyEnrolments))
        mockGetLatestPayments(Future successful Left("Unauthorized! Reference in payload doesn't match with logged in UTR"))

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> "appSelfAssessment", "reference" -> "1234567"))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 401
      }

      "return 404, reference matches the UTR but payment service returns not found" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), Some(utr), saOnlyEnrolments))
        mockGetLatestPayments(Future successful Right(None))

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> "appSelfAssessment", "reference" -> utr))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 404
      }

      "return 500 when reference matches the UTR and payment service returns internal server error" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), Some(utr), saOnlyEnrolments))
        mockGetLatestPayments(Future successful Left("Unknown response"))

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> "appSelfAssessment", "reference" -> utr))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 500
      }
    }

    "taxType = appSimpleAssessment " should {

      "return latest Payment, when  reference == Auth user charge reference and no enrolments and utr" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), None, Set.empty))
        mockGetLatestPayments(Future successful Right(Some(latestPaymentsResponse)))

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> "appSimpleAssessment", "reference" -> chargeRef1))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 200
        val response = contentAsJson(result).as[LatestPaymentsResponse]
        response.payments.size               shouldBe 2
        response.payments.head.amountInPence shouldBe 11100
        response.payments.head.date.toString shouldBe "2022-05-07"
      }

      "return 401, when reference !== Auth reference charge reference" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), None, Set.empty))
        mockGetLatestPayments(Future successful Left("Unauthorized! Reference in payload doesn't match with logged in Reference"))

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> "appSimpleAssessment", "reference" -> "1234567"))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 401
      }

      "return 404, reference matches the Logged in charge reference  but payment service returns not found" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), None, Set.empty))
        mockGetLatestPayments(Future successful Right(None))

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> "appSimpleAssessment", "reference" -> utr))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 404
      }

      "return 500 when reference matches the UTR and payment service returns internal server error" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), None, Set.empty))
        mockGetLatestPayments(Future successful Left("Unknown response"))

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> "appSimpleAssessment", "reference" -> utr))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 500
      }
    }

    "taxType = blank" should {

      "return 400 with malformed json error" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> " ", "reference" -> utr))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 400
      }
    }

    "taxType = other" should {

      "return 400 with malformed json error" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("taxType" -> " ", "reference" -> utr))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 400
      }
    }

    "taxType not present" should {

      "return 400 with malformed json error" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()

        val request = FakeRequest("POST", s"/payments/latest-payments")
          .withHeaders(acceptJsonHeader, contentHeader)
          .withBody(Json.obj("reference" -> utr))

        val result = sut.latestPayments(journeyId)(request)
        status(result) shouldBe 400
      }
    }

  }

  "getPayByCardURL" when {

    "taxType = appSelfAssessment" should {

      "return 200 if payload ref == Auth UTR for IR-SA  enrolment only" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), Some(utr), saOnlyEnrolments))
        mockPayByCardUrl(Future successful payByCardResponse)

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> "appSelfAssessment", "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 200
        val response = contentAsJson(result).as[PayByCardResponse]
        response.payByCardUrl shouldBe "/pay/choose-a-way-to-pay?traceId=12345678"
      }

      "return 200 if payload ref == Auth UTR for bothIR-SA and MTD  enrolments" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), Some(utr), saMTDEnrolments))
        mockPayByCardUrl(Future successful payByCardResponse)

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> "appSelfAssessment", "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 200
        val response = contentAsJson(result).as[PayByCardResponse]
        response.payByCardUrl shouldBe "/pay/choose-a-way-to-pay?traceId=12345678"
      }

      "return 200 if payload ref == Auth UTR for  MTD only enrolments (UTR found fro the NIno)" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), None, mtdOnlyEnrolments))
        stubGetUTRByNino(Future.successful(Some(SaUtr(utr))))
        mockPayByCardUrl(Future successful payByCardResponse)

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> "appSelfAssessment", "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 200
        val response = contentAsJson(result).as[PayByCardResponse]
        response.payByCardUrl shouldBe "/pay/choose-a-way-to-pay?traceId=12345678"
      }

      "return 401, if payload ref != Auth UTR " in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), Some(utr), mtdOnlyEnrolments))
        mockPayByCardUrl(Future failed UpstreamErrorResponse("Error", 401, 401))

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> "appSelfAssessment", "reference" -> "12344"))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 401
      }

      "return 401, if UTR is not fetched from either auth call or citizen details" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), None, mtdOnlyEnrolments))
        stubGetUTRByNino(Future.successful(None))
        mockPayByCardUrl(Future failed UpstreamErrorResponse("Error", 401, 401))

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> "appSelfAssessment", "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 401
      }

      "return 500, if payment service returns 5XX" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), Some(utr), mtdOnlyEnrolments))
        mockPayByCardUrl(Future failed UpstreamErrorResponse("Error", 502, 502))

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> "appSelfAssessment", "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 500
      }

      "return 400- malformed json if amountInPence is absent" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("taxType" -> "appSelfAssessment", "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 400
      }

      "return 400- malformed json if reference is absent" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> "appSelfAssessment"))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 400
      }

    }

    "taxType = appSimpleAssessment" should {

      "return 200 if payload ref == Auth charge reference " in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), Some(utr), saOnlyEnrolments))
        mockPayByCardUrl(Future successful payByCardResponse)

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(
            Json.obj("amountInPence" -> 1234, "taxType" -> "appSimpleAssessment", "taxYear" -> 2023, "reference" -> utr)
          )

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 200
        val response = contentAsJson(result).as[PayByCardResponse]
        response.payByCardUrl shouldBe "/pay/choose-a-way-to-pay?traceId=12345678"
      }

      "return 200 if payload ref == Auth charge reference and utr is None and NO enrolemnts" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), None, Set.empty))
        mockPayByCardUrl(Future successful payByCardResponse)

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(
            Json.obj("amountInPence" -> 1234, "taxType" -> "appSimpleAssessment", "taxYear" -> 2023, "reference" -> utr)
          )

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 200
        val response = contentAsJson(result).as[PayByCardResponse]
        response.payByCardUrl shouldBe "/pay/choose-a-way-to-pay?traceId=12345678"
      }

      "return 401, if pay by card service calls fails with 401" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), None, Set.empty))
        mockPayByCardUrl(Future failed UpstreamErrorResponse("Error", 401, 401))

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> "appSimpleAssessment", "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 401
      }

      "return 404, if pay by card service calls fails with 404" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), None, Set.empty))
        mockPayByCardUrl(Future failed UpstreamErrorResponse("Error", 404, 404))

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> "appSimpleAssessment", "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 404
      }

      "return 500, if pay by card service calls fails with 5XX" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()
        stubGetNinoAndUTRFromAuth(getNinoUtrEnrolmentResponse(Some(nino), None, Set.empty))
        mockPayByCardUrl(Future failed UpstreamErrorResponse("Error", 500, 500))

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> "appSimpleAssessment", "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 500
      }

      "return 400-malformed json if amountInPence is missing" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("taxType" -> "appSimpleAssessment", "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 400
      }

      "return 400-malformed json if reference is missing" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> "appSimpleAssessment"))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 400
      }

    }

    "taxType  = Blank" should {
      "return 400, malformed json" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> " ", "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 400
      }
    }

    "taxType  not present" should {
      "return 400, malformed json" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 400
      }
    }

    "taxType  = Other" should {
      "return 400, malformed json" in {
        stubAuthorisationGrantAccess(authorisedResponse)
        shutteringDisabled()

        val request = FakeRequest("POST", s"/payments/pay-by-card")
          .withHeaders("Accept" -> "application/vnd.hmrc.1.0+json", "Content-Type" -> "application/json")
          .withBody(Json.obj("amountInPence" -> 1234, "taxType" -> "Other", "reference" -> utr))

        val result = sut.getPayByCardURL(journeyId)(request)
        status(result) shouldBe 400
      }
    }

  }

  private def mockInitiatePayment(future: Future[InitiatePaymentResponse]) =
    (mockOpenBankingService
      .initiatePayment(_: String, _: JourneyId)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(future)

  private def mockUpdatePayment(future: Future[InitiatePaymentResponse]) =
    (mockOpenBankingService
      .updatePayment(_: String, _: JourneyId)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(future)

  private def shutteringDisabled(): CallHandler[Future[Shuttering]] =
    mockShutteringResponse(Shuttering(shuttered = false))

  private def mockGetPaymentStatus(f: Future[PaymentStatusResponse]) =
    (mockOpenBankingService
      .getPaymentStatus(_: String, _: JourneyId)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(f)

  private def stubPaymentEvent() =
    (mockAuditService
      .sendPaymentEvent(_: SessionDataResponse, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future successful Success)

  private def mockGetUrlConsumed(f: Future[UrlConsumedResponse]) =
    (mockOpenBankingService
      .urlConsumed(_: String, _: JourneyId)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(f)

  private def mockGetSession(future: Future[SessionDataResponse]) =
    (mockOpenBankingService
      .getSession(_: String, _: JourneyId)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(future)

  private def mockSendEmail() =
    (mockOpenBankingService
      .sendEmail(_: String, _: JourneyId, _: Origin)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *, *)
      .returning(Future.successful(Success))

  private def mockGetLatestPayments(future: Future[Either[String, Option[LatestPaymentsResponse]]]) =
    (mockPaymentsService
      .getLatestPayments(_: Option[String], _: Option[String], _: Option[String], _: Option[TaxTypeEnum.Value], _: JourneyId)(
        _: ExecutionContext,
        _: HeaderCarrier
      ))
      .expects(*, *, *, *, *, *, *)
      .returning(future)

  private def mockPayByCardUrl(future: Future[PayByCardResponse]): Unit =
    (mockPaymentsService
      .getPayByCardUrl(_: PayByCardRequestGeneric, _: Option[String], _: Option[SaUtr], _: JourneyId)(_: ExecutionContext, _: HeaderCarrier))
      .expects(*, *, *, journeyId, *, *)
      .returning(future)

}
