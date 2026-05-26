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

package uk.gov.hmrc.mobilepayments.services

import org.scalatest.time.SpanSugar.convertDoubleToGrainOfTime
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mobilepayments.MobilePaymentsTestData
import uk.gov.hmrc.mobilepayments.common.BaseSpec
import uk.gov.hmrc.mobilepayments.connectors.PaymentsConnector
import uk.gov.hmrc.mobilepayments.controllers.errors.MalformedRequestException
import uk.gov.hmrc.mobilepayments.domain.dto.response.PayApiPayByCardResponse
import uk.gov.hmrc.mobilepayments.domain.types.JourneyId
import uk.gov.hmrc.mobilepayments.domain.PaymentRecordListFromApi
import uk.gov.hmrc.mobilepayments.domain.dto.request.{PayByCardRequestGeneric, TaxTypeEnum}

import java.time.LocalDate
import scala.concurrent.{Await, Future}

class PaymentsServiceSpec extends BaseSpec with MobilePaymentsTestData {

  private val mockConnector: PaymentsConnector = mock[PaymentsConnector]
  private val saUtr: SaUtr = SaUtr("CS700100A")
  private val nino: Option[String] = Some("CS700100A")

  private val sut = new PaymentsService(mockConnector)

  "when getLatestPayments invoked and connector returns success with payments then" should {
    "return successful payments made within the last 14 days" in {
      mockLatestPayments(Future successful Right(Some(paymentsResponse())))

      val result =
        Await.result(sut.getLatestPayments(Some(saUtr.value), Some(saUtr.value), Some(TaxTypeEnum.appSelfAssessment), journeyId), 0.5.seconds)
      result.toOption.get.get.payments.size               shouldBe 2
      result.toOption.get.get.payments.head.amountInPence shouldBe 11100
    }

    "return None if no successful payments made within the last 14 days" in {
      mockLatestPayments(Future successful Right(Some(paymentsResponse(LocalDate.of(2022, 5, 1)))))

      val result =
        Await.result(sut.getLatestPayments(Some(saUtr.value), Some(saUtr.value), Some(TaxTypeEnum.appSelfAssessment), journeyId), 0.5.seconds)
      result.toOption.get shouldBe None
    }

    "return None if no payments made within the last 14 days" in {
      mockLatestPayments(Future successful Right(None))

      val result =
        Await.result(sut.getLatestPayments(Some(saUtr.value), Some(saUtr.value), Some(TaxTypeEnum.appSelfAssessment), journeyId), 0.5.seconds)
      result.toOption.get shouldBe None
    }

    "return Error if exception returned from PaymentsConnector" in {
      mockLatestPayments(Future successful Left("Error while calling pay api"))

      val result =
        Await.result(sut.getLatestPayments(Some(saUtr.value), Some(saUtr.value), Some(TaxTypeEnum.appSelfAssessment), journeyId), 0.5.seconds)
      result.swap.getOrElse("") shouldBe "Error while calling pay api"
    }

    "return Error if utr is missing" in {

      val result =
        Await.result(sut.getLatestPayments(None, Some(saUtr.value), Some(TaxTypeEnum.appSelfAssessment), journeyId), 0.5.seconds)
      result.swap.getOrElse("") shouldBe "Unauthorized! UTR or reference is missing from payload/Enrolments"
    }

    "return Error if utr and reference don't match" in {

      val result =
        Await.result(sut.getLatestPayments(Some("123456789"), Some(saUtr.value), Some(TaxTypeEnum.appSelfAssessment), journeyId), 0.5.seconds)
      result.swap.getOrElse("") shouldBe "Unauthorized! Reference in payload doesn't match with logged in UTR"
    }
  }

  "when getPayByCardUrl invoked with self assessment and connector returns success with URL then" should {
    "return pay by card URL" in {
      mockPayByCardUrl(Future successful PayApiPayByCardResponse("/payByCard"))

      val result = Await.result(
        sut.getPayByCardUrl(PayByCardRequestGeneric(2000, TaxTypeEnum.appSelfAssessment, reference = saUtr.value), nino, journeyId),
        0.5.seconds
      )
      result.payByCardUrl shouldBe "/payByCard"
    }

    "return an error when connector fails" in {
      mockPayByCardUrl(Future failed UpstreamErrorResponse("Error", 400, 400))

      intercept[UpstreamErrorResponse] {
        Await.result(sut.getPayByCardUrl(PayByCardRequestGeneric(2000, TaxTypeEnum.appSelfAssessment, reference = saUtr.value), nino, journeyId),
                     0.5.seconds
                    )
      }
    }
  }

  "when getPayByCardUrlGeneric invoked with simple assessment and connector returns success with URL then" should {
    "return pay by card URL" in {
      mockPayCardUrlSimpleAssessment(Future successful PayApiPayByCardResponse("/payByCard"))

      val result = Await.result(
        sut.getPayByCardUrl(
          PayByCardRequestGeneric(2000, TaxTypeEnum.appSimpleAssessment, taxYear = Some(2023), reference = "CS700100A"),
          nino,
          journeyId
        ),
        0.5.seconds
      )
      result.payByCardUrl shouldBe "/payByCard"
    }

    "return an error when connector fails" in {
      mockPayCardUrlSimpleAssessment(Future failed UpstreamErrorResponse("Error", 400, 400))

      intercept[UpstreamErrorResponse] {
        Await.result(
          sut.getPayByCardUrl(PayByCardRequestGeneric(2000, TaxTypeEnum.appSimpleAssessment, taxYear = Some(2023), reference = "CS700100A"),
                              nino,
                              journeyId
                             ),
          0.5.seconds
        )
      }
    }

    "return a Malformed Request Exception when the TaxYear isn't sent with the request" in {
      intercept[MalformedRequestException] {
        Await.result(sut.getPayByCardUrl(PayByCardRequestGeneric(2000, TaxTypeEnum.appSimpleAssessment, reference = "CS700100A"), nino, journeyId),
                     0.5.seconds
                    )
      }
    }
  }

  private def mockLatestPayments(future: Future[Either[String, Option[PaymentRecordListFromApi]]]): Unit =
    (mockConnector
      .getPayments(_: Option[String], _: Option[String], _: Option[TaxTypeEnum.Value], _: JourneyId)(_: HeaderCarrier))
      .expects(*, *, *, journeyId, hc)
      .returning(future)

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
