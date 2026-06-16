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

package uk.gov.hmrc.mobilepayments

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.mobilepayments.domain.dto.request.CreateSessionRequest
import uk.gov.hmrc.mobilepayments.domain.dto.response.*
import uk.gov.hmrc.mobilepayments.domain.{Bank, BankGroupData, PaymentRecordListFromApi, Shuttering}
import uk.gov.hmrc.mobilepayments.models.openBanking.{OriginSpecificSessionData, SessionData}
import uk.gov.hmrc.mobilepayments.models.openBanking.response.{CreateSessionDataResponse, InitiatePaymentResponse}
import uk.gov.hmrc.time.TaxYear

import java.time.LocalDate
import scala.io.Source

trait MobilePaymentsTestData {
  val nino: Option[String] = Some("CS700100A")

  val chargeRef1: String = "XC123456789"
  val chargeRef2 = "XC987654321"
  val chargeRefList = List(chargeRef1, chargeRef2)

  lazy val banksResponse: List[Bank] = Json.fromJson[List[Bank]](js("banks-response")).get

  lazy val banksResponseGrouped: List[BankGroupData] =
    Json.fromJson[List[BankGroupData]](js("banks-response-grouped")).get

  lazy val paymentStatusOpenBankingResponse: OpenBankingPaymentStatusResponse =
    Json.fromJson[OpenBankingPaymentStatusResponse](js("payment-status-ob-response")).get

  lazy val createSessionRequest: CreateSessionRequest =
    Json.fromJson[CreateSessionRequest](js("create-session-request")).get

  lazy val createSessionNewSARequest: CreateSessionRequest =
    Json.fromJson[CreateSessionRequest](js("create-new-session-sa-request")).get

  lazy val createSessionNewSIRequest: CreateSessionRequest =
    Json.fromJson[CreateSessionRequest](js("create-new-session-si-request")).get

  lazy val createSessionIncorrectFieldsSA: CreateSessionRequest =
    Json.fromJson[CreateSessionRequest](js("create-session-incorrect-fields-sa-request")).get

  lazy val createSessionIncorrectFieldsSI: CreateSessionRequest =
    Json.fromJson[CreateSessionRequest](js("create-session-incorrect-fields-si-request")).get

  lazy val createSessionIncorrectFieldsOld: CreateSessionRequest =
    Json.fromJson[CreateSessionRequest](js("create-session-incorrect-fields-old-request")).get

  lazy val paymentStatusResponse: PaymentStatusResponse =
    Json.fromJson[PaymentStatusResponse](js("payment-status-response")).get

  lazy val shutteredResponse: Shuttering = Json.fromJson[Shuttering](js("shuttered-response")).get

  lazy val createSessionDataResponse: CreateSessionDataResponse =
    Json.fromJson[CreateSessionDataResponse](js("create-session-data-response")).get

  lazy val sessionDataResponse: SessionDataResponse =
    Json.fromJson[SessionDataResponse](js("session-data-controller-response")).get

  lazy val sessionDataSimpleAssessmentResponse: SessionDataResponse =
    Json.fromJson[SessionDataResponse](js("session-data-simple-assessment-controller-response")).get

  lazy val sessionDataPaymentFinalisedResponse: SessionDataResponse =
    Json.fromJson[SessionDataResponse](js("session-data-controller-payment-finalised-response")).get

  lazy val paymentInitiatedResponse: InitiatePaymentResponse =
    Json.fromJson[InitiatePaymentResponse](js("payment-initiated-response")).get

  lazy val paymentInitiatedUpdateResponse: InitiatePaymentResponse =
    Json.fromJson[InitiatePaymentResponse](js("payment-initiated-update-response")).get

  lazy val paymentSessionResponse: InitiatePaymentResponse =
    Json.fromJson[InitiatePaymentResponse](js("payment-session-response")).get

  lazy val sessionInitiatedDataResponse: SessionData[OriginSpecificSessionData] =
    Json.fromJson[SessionData[OriginSpecificSessionData]](js("session-data-initiated-response")).get

  lazy val sessionBankSelectedDataResponse: SessionData[OriginSpecificSessionData] =
    Json.fromJson[SessionData[OriginSpecificSessionData]](js("session-data-bank-selected-response")).get

  lazy val sessionPaymentFinishedDataResponse: SessionData[OriginSpecificSessionData] =
    Json.fromJson[SessionData[OriginSpecificSessionData]](js("session-data-payment-finished-response")).get

  lazy val sessionPaymentFinalisedDataResponse: SessionData[OriginSpecificSessionData] =
    Json.fromJson[SessionData[OriginSpecificSessionData]](js("session-data-payment-finalised-response")).get

  lazy val latestPaymentsResponse: LatestPaymentsResponse =
    Json.fromJson[LatestPaymentsResponse](js("latest-payments-response")).get

  lazy val payApiPayByCardResponse: PayApiPayByCardResponse =
    Json.fromJson[PayApiPayByCardResponse](js("pay-api-pay-by-card-response")).get

  lazy val payByCardResponse: PayByCardResponse =
    Json.fromJson[PayByCardResponse](js("pay-by-card-response")).get

  val paymentDataResponseTestJson: String =
    Source.fromFile("test-common/uk/gov/hmrc/mobilepayments/resources/test-p800-response-full.json").mkString

  def paymentsResponseString(date: LocalDate = LocalDate.now()): String =
    json("payments-response").replace("<DATE>", date.toString).replace("<DATE2>", date.minusDays(10).toString)

  def getTestJson(
    path: String,
    nino: String
  ): JsValue =
    Json.parse(updateJson(path.replace("<NINO>", nino)))

  private def updateJson(response: String): String = {
    val regex = "\"<CY-(\\d)>\"".r
    regex.replaceAllIn(response, int => (TaxYear.current.startYear - int.group(1).toInt).toString)
  }

  def paymentsResponse(date: LocalDate = LocalDate.now()): PaymentRecordListFromApi =
    Json
      .fromJson[PaymentRecordListFromApi](
        Json.parse(
          json("payments-response").replace("<DATE>", date.toString).replace("<DATE2>", date.minusDays(10).toString)
        )
      )
      .get

  lazy val rawMalformedJson: String = "{\"data\": [{,]}"
  lazy val banksResponseJson: String = json("banks-response")
  lazy val sessionDataResponseJson: String = json("session-data-initiated-response")
  lazy val createSessionDataResponseJson: String = json("create-session-data-response")
  lazy val sessionDataBankSelectedResponseJson: String = json("session-data-bank-selected-response")
  lazy val sessionDataPaymentFinalisedResponseJson: String = json("session-data-payment-finalised-response")
  lazy val createPaymentRequestJson: String = json("create-payment-request")
  lazy val paymentInitiatedResponseJson: String = json("payment-initiated-response")
  lazy val paymentInitiatedUpdateResponseJson: String = json("payment-initiated-update-response")
  lazy val paymentStatusResponseJson: String = json("payment-status-ob-response")
  lazy val payApiPayByCardResponseJson: String = json("pay-api-pay-by-card-response")
  lazy val latestPaymentsSelfAssessmentJson: String = json("self-assessment-latest-payments-request")
  lazy val latestPaymentsSimpleAssessmentJson: String = json("simple-assessment-latest-payments-request")

  lazy val sessionDataPaymentFinalisedEmailSentResponseJson: String = json(
    "session-data-payment-finalised-email-sent-response"
  )

  lazy val sessionDataPaymentFinalisedSimpleAssessmentResponseJson: String = json(
    "session-data-payment-finalised-simple-assessment-email-sent-response"
  )

  private def json(fileName: String): String = {
    val source = Source.fromFile(s"test-common/uk/gov/hmrc/mobilepayments/resources/test-$fileName.json")
    val raw = source.getLines().mkString
    source.close()
    raw
  }

  private def js(fileName: String): JsValue =
    Json.parse(json(fileName))
}
