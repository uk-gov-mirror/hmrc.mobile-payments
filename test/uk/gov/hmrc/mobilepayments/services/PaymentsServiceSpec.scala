/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.mobilepayments.services

import org.scalatest.time.SpanSugar.convertDoubleToGrainOfTime
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mobilepayments.MobilePaymentsTestData
import uk.gov.hmrc.mobilepayments.common.BaseSpec
import uk.gov.hmrc.mobilepayments.connectors.PaymentsConnector
import uk.gov.hmrc.mobilepayments.controllers.errors.{FailToMatchTaxIdOnAuth, MalformedRequestException}
import uk.gov.hmrc.mobilepayments.domain.dto.response.{LatestPaymentsResponse, PayApiPayByCardResponse}
import uk.gov.hmrc.mobilepayments.domain.types.JourneyId
import uk.gov.hmrc.mobilepayments.domain.PaymentRecordListFromApi
import uk.gov.hmrc.mobilepayments.domain.dto.request.{PayByCardRequestGeneric, TaxTypeEnum}

import java.time.LocalDate
import scala.concurrent.{Await, ExecutionContext, Future}

class PaymentsServiceSpec extends BaseSpec with MobilePaymentsTestData {

  private val mockConnector: PaymentsConnector = mock[PaymentsConnector]
  private val mockP800Service: P800Service = mock[P800Service]
  private val saUtr: SaUtr = SaUtr("CS700100A")

  private val sut = new PaymentsService(mockConnector, mockP800Service)

  "getLatestPayments" when {
    "taxType = appSelfAssessment and reference equals auth utr" should {
      "return successful payment" in {

        mockLatestPayments(Future successful Right(Some(paymentsResponse())))

        val result =
          Await.result(sut.getLatestPayments(None, Some(saUtr.value), Some(saUtr.value), Some(TaxTypeEnum.appSelfAssessment), journeyId), 0.5.seconds)
        result.toOption.get.get.payments.size               shouldBe 2
        result.toOption.get.get.payments.head.amountInPence shouldBe 11100
      }
    }

    "taxType = appSelfAssessment and reference equals auth utr and no succesful payment for last 14 days" should {
      "return successful payment" in {

        mockLatestPayments(Future successful Right(Some(paymentsResponse(LocalDate.of(2022, 5, 1)))))

        val result =
          Await.result(sut.getLatestPayments(None, Some(saUtr.value), Some(saUtr.value), Some(TaxTypeEnum.appSelfAssessment), journeyId), 0.5.seconds)
        result.toOption.get shouldBe None
      }
    }

    "taxType = appSelfAssessment and reference equals auth utr and no  payment made for last 14 days" should {
      "return successful payment" in {

        mockLatestPayments(Future successful Right(None))

        val result =
          Await.result(sut.getLatestPayments(None, Some(saUtr.value), Some(saUtr.value), Some(TaxTypeEnum.appSelfAssessment), journeyId), 0.5.seconds)
        result.toOption.get shouldBe None
      }
    }

    "taxType = appSelfAssessment and reference equals auth utr" should {
      "return error if exception happened in downstream system" in {

        mockLatestPayments(Future successful Left("Error while calling pay api"))

        val result =
          Await.result(sut.getLatestPayments(None, Some(saUtr.value), Some(saUtr.value), Some(TaxTypeEnum.appSelfAssessment), journeyId), 0.5.seconds)
        result.swap.getOrElse("") shouldBe "Error while calling pay api"
      }
    }

    "taxType = appSelfAssessment and reference is not equal to auth UTR" should {
      "return Left of Unauthorized" in {

        val result: Either[String, Option[LatestPaymentsResponse]] =
          Await.result(sut.getLatestPayments(None, Some(saUtr.value), Some("12345678"), Some(TaxTypeEnum.appSelfAssessment), journeyId), 0.5.seconds)
        result shouldBe (Left("Unauthorized! Reference in payload doesn't match with logged in UTR"))
      }
    }

    "taxType = appSelfAssessment and reference is not present" should {
      "return Left of Malformed json" in {

        val result: Either[String, Option[LatestPaymentsResponse]] =
          Await.result(sut.getLatestPayments(None, Some(saUtr.value), None, Some(TaxTypeEnum.appSelfAssessment), journeyId), 0.5.seconds)
        result shouldBe (Left("Malformed json"))
      }
    }

    "taxType = appSimpleAssessment and reference equals auth reference" should {
      "return successful payment" in {

        mockGetChargeReferenceList(Future.successful(chargeRefList))
        mockLatestPayments(Future successful Right(Some(paymentsResponse())))

        val result =
          Await.result(sut.getLatestPayments(nino, None, Some(chargeRef1), Some(TaxTypeEnum.appSimpleAssessment), journeyId), 0.5.seconds)
        result.toOption.get.get.payments.size               shouldBe 2
        result.toOption.get.get.payments.head.amountInPence shouldBe 11100
      }
    }

    "taxType = appSimpleAssessment and reference is not equal auth reference" should {
      "return successful payment" in {

        mockGetChargeReferenceList(Future.successful(chargeRefList))

        val result =
          Await.result(sut.getLatestPayments(nino, None, Some("1213e4"), Some(TaxTypeEnum.appSimpleAssessment), journeyId), 0.5.seconds)
        result shouldBe (Left("Unauthorized! Reference in payload doesn't match with Charge Reference of the user"))
      }
    }

    "taxType = appSimpleAssessment and reference is not present" should {
      "return Left of Malformed json" in {

        val result: Either[String, Option[LatestPaymentsResponse]] =
          Await.result(sut.getLatestPayments(None, None, None, Some(TaxTypeEnum.appSimpleAssessment), journeyId), 0.5.seconds)
        result shouldBe (Left("Malformed json"))
      }
    }

    "taxType = blank " should {
      "return Left of Malformed json" in {

        val result: Either[String, Option[LatestPaymentsResponse]] =
          Await.result(sut.getLatestPayments(None, None, None, None, journeyId), 0.5.seconds)
        result shouldBe (Left("Malformed json"))
      }
    }

  }

  "getPayByCardUrl" when {

    "taxType = appSelfAssessment" when {
      "reference equals the auth utr" should {
        "return valid pay by card response" in {
          mockPayByCardUrl(Future successful PayApiPayByCardResponse("/payByCard"))
          val result = Await.result(
            sut.getPayByCardUrl(PayByCardRequestGeneric(2000, TaxTypeEnum.appSelfAssessment, reference = saUtr.value), None, Some(saUtr), journeyId),
            0.5.seconds
          )
          result.payByCardUrl shouldBe "/payByCard"
        }

      }
      "reference is not equal to the auth utr" should {
        "return FailToMatchTaxIdOnAuth" in {

          intercept[FailToMatchTaxIdOnAuth](
            Await.result(
              sut.getPayByCardUrl(PayByCardRequestGeneric(2000, TaxTypeEnum.appSelfAssessment, reference = saUtr.value),
                                  None,
                                  Some(SaUtr("123456")),
                                  journeyId
                                 ),
              0.5.seconds
            )
          )

        }

      }
      "reference is equal to auth utr but connector" should {
        "return an error " in {
          mockPayByCardUrl(Future failed UpstreamErrorResponse("Error", 400, 400))

          intercept[UpstreamErrorResponse] {
            Await.result(
              sut.getPayByCardUrl(PayByCardRequestGeneric(2000, TaxTypeEnum.appSelfAssessment, reference = saUtr.value),
                                  None,
                                  Some(saUtr),
                                  journeyId
                                 ),
              0.5.seconds
            )
          }
        }
      }
    }

    "taxTYpe= appSimpleAssessment" when {
      "reference is equal to Auth reference" should {
        "return valid pay by card response " in {
          mockGetChargeReferenceList(Future.successful(chargeRefList))
          mockPayCardUrlSimpleAssessment(Future successful PayApiPayByCardResponse("/payByCard"))
          val result = Await.result(
            sut.getPayByCardUrl(PayByCardRequestGeneric(2000, TaxTypeEnum.appSimpleAssessment, reference = chargeRef1, taxYear = Some(2024)),
                                nino,
                                None,
                                journeyId
                               ),
            0.5.seconds
          )
          result.payByCardUrl shouldBe "/payByCard"
        }
      }
      "reference is not equal to Auth reference" should {
        "return FailToMatchTaxIdOnAuth" in {
          mockGetChargeReferenceList(Future.successful(chargeRefList))

          intercept[FailToMatchTaxIdOnAuth](
            Await.result(
              sut.getPayByCardUrl(PayByCardRequestGeneric(2000, TaxTypeEnum.appSimpleAssessment, reference = "23455", taxYear = Some(2024)),
                                  nino,
                                  None,
                                  journeyId
                                 ),
              0.5.seconds
            )
          )

        }
      }
      "p800 service fails" should {
        "return exception" in {
          mockGetChargeReferenceList(Future failed UpstreamErrorResponse("Error", 400, 400))

          intercept[UpstreamErrorResponse](
            Await.result(
              sut.getPayByCardUrl(PayByCardRequestGeneric(2000, TaxTypeEnum.appSimpleAssessment, reference = chargeRef1, taxYear = Some(2024)),
                                  nino,
                                  None,
                                  journeyId
                                 ),
              0.5.seconds
            )
          )

        }
      }
      "p800 service passes but payment service fails" should {
        "return exception" in {
          mockGetChargeReferenceList(Future.successful(chargeRefList))
          mockPayCardUrlSimpleAssessment(Future failed UpstreamErrorResponse("Error", 400, 400))

          intercept[UpstreamErrorResponse](
            Await.result(
              sut.getPayByCardUrl(PayByCardRequestGeneric(2000, TaxTypeEnum.appSimpleAssessment, reference = chargeRef1, taxYear = Some(2024)),
                                  nino,
                                  None,
                                  journeyId
                                 ),
              0.5.seconds
            )
          )

        }
      }

      "taxYear not passed" should {
        "return MalformedRequestException" in {

          intercept[MalformedRequestException](
            Await.result(
              sut.getPayByCardUrl(PayByCardRequestGeneric(2000, TaxTypeEnum.appSimpleAssessment, reference = chargeRef1, taxYear = None),
                                  nino,
                                  None,
                                  journeyId
                                 ),
              0.5.seconds
            )
          )

        }
      }
    }
  }

  private def mockLatestPayments(future: Future[Either[String, Option[PaymentRecordListFromApi]]]): Unit =
    (mockConnector
      .getPayments(_: Option[String], _: Option[String], _: Option[TaxTypeEnum.Value], _: JourneyId)(_: HeaderCarrier))
      .expects(*, *, *, journeyId, hc)
      .returning(future)

  private def mockGetChargeReferenceList(response: Future[List[String]]) =
    (mockP800Service
      .getChargeRefernceList(_: Option[String], _: Int)(_: ExecutionContext, _: HeaderCarrier))
      .expects(*, *, *, *)
      .returning(response)

  private def mockPayByCardUrl(future: Future[PayApiPayByCardResponse]): Unit =
    (mockConnector
      .getPayByCardUrl(_: Long, _: SaUtr, _: JourneyId)(_: HeaderCarrier))
      .expects(*, *, journeyId, hc)
      .returning(future)

  private def mockPayCardUrlSimpleAssessment(future: Future[PayApiPayByCardResponse]): Unit =
    (mockConnector
      .getPayByCardUrlSimpleAssessment(_: Long, _: String, _: String, _: Int, _: JourneyId)(_: HeaderCarrier))
      .expects(*, *, *, *, journeyId, hc)
      .returning(future)
}
