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

import play.api.test.Helpers.await
import org.apache.pekko.http.scaladsl.model.Uri
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.mobilepayments.MobilePaymentsTestData
import uk.gov.hmrc.mobilepayments.common.BaseSpec
import uk.gov.hmrc.mobilepayments.connectors.OpenBankingConnector
import uk.gov.hmrc.mobilepayments.controllers.errors.{FailToMatchTaxIdOnAuth, MalformedRequestException, UtrNotFoundOnAccount}
import uk.gov.hmrc.mobilepayments.domain.dto.request.{CreateSessionRequest, OriginSpecificData, SelfAssessmentOriginSpecificData, SimpleAssessmentOriginSpecificData, TaxTypeEnum}
import uk.gov.hmrc.mobilepayments.domain.dto.response.OpenBankingPaymentStatusResponse
import uk.gov.hmrc.mobilepayments.domain.types.JourneyId
import uk.gov.hmrc.mobilepayments.domain.Bank
import uk.gov.hmrc.mobilepayments.models.openBanking.response.{CreateSessionDataResponse, InitiatePaymentResponse}
import uk.gov.hmrc.mobilepayments.models.openBanking.{OriginSpecificSessionData, SessionData}

import java.time.LocalDate
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

class OpenBankingServiceSpec extends BaseSpec with MobilePaymentsTestData {

  private val mockConnector: OpenBankingConnector = mock[OpenBankingConnector]
  private val mockP800Service: P800Service = mock[P800Service]
  private val amount: BigDecimal = 102.85
  private val amountInPence: BigDecimal = (amount * 100).longValue
  private val saUtr: SaUtr = SaUtr("CS700100A")
  private val bankId: String = "asd-123"
  private val sessionDataId: String = "51cc67d6-21da-11ec-9621-0242ac130002"
  private val returnUrl: String = "https://tax.service.gov.uk/mobile-payments/ob-payment-result"
  private val paymentUrl: Uri = "https://some-bank.com?param=dosomething"
  private val selfAssessmentSpecificData: SelfAssessmentOriginSpecificData = SelfAssessmentOriginSpecificData(saUtr)

  private val simpleAssessmentSpecificData: SimpleAssessmentOriginSpecificData = SimpleAssessmentOriginSpecificData(
    chargeRef1
  )

  private val sut = new OpenBankingService(mockConnector, mockP800Service, returnUrl)

  "when getBanks invoked and connector returns success with banks then" should {
    "return banks" in {
      mockBanks(Future successful banksResponse)

      val result = Await.result(sut.getBanks(journeyId), 0.5.seconds)
      result.data.size shouldBe 10
    }
  }

  "when getBanks invoked and connector returns NotFoundException then" should {
    "return an error" in {
      mockBanks(Future failed UpstreamErrorResponse("Error", 400, 400))

      intercept[UpstreamErrorResponse] {
        await(sut.getBanks(journeyId))
      }
    }
  }

  "Create session method" when {

    val createSessionRequest = CreateSessionRequest(amount        = Some(100),
                                                    saUtr         = Some(saUtr),
                                                    amountInPence = Some(amountInPence),
                                                    reference     = Some(saUtr.utr),
                                                    taxType       = Some(TaxTypeEnum.appSelfAssessment)
                                                   )

    "taxType == appSelfAssessment" when {

      "amount = None, payload reference matches auth UTR and request sautr" should {

        "create session" in {
          mockCreateSession(Future successful createSessionDataResponse)
          val result =
            Await.result(sut.createSession(createSessionRequest.copy(amount = None, saUtr = Some(saUtr)), journeyId, sautrOpt = Some(saUtr)),
                         0.5.seconds
                        )
          result.sessionDataId.value shouldEqual "51cc67d6-21da-11ec-9621-0242ac130002"
        }
      }

      "amount = None, payload reference matches auth UTR but request sautr is diff" should {

        "Throw exception: FailToMatchTaxIdOnAuth" in {

          intercept[FailToMatchTaxIdOnAuth] {
            Await.result(
              sut.createSession(createSessionRequest.copy(amount = None, saUtr = Some(SaUtr("123456"))), journeyId, sautrOpt = Some(saUtr)),
              0.5.seconds
            )
          }
        }
      }

      "amount = None, auth Utr is missing, request sautr = request reference" should {

        "Throw exception : UtrNotFoundOnAccount" in {

          intercept[UtrNotFoundOnAccount] {
            Await.result(
              sut.createSession(createSessionRequest.copy(amount = None, saUtr = Some(saUtr)), journeyId, sautrOpt = None),
              0.5.seconds
            )
          }
        }
      }

      "amount = None, auth utr equals reference and requested utr, amount in pence missing" should {

        "Throw exception : MalformedRequestException" in {

          intercept[MalformedRequestException] {
            Await.result(
              sut.createSession(createSessionRequest.copy(amount = None, amountInPence = None, saUtr = Some(saUtr)),
                                journeyId,
                                sautrOpt = Some(saUtr)
                               ),
              0.5.seconds
            )
          }
        }
      }
    }

    "taxType == appSimpleAssessment" when {

      "amount = None, payload reference matches auth reference" should {

        "create session" in {
          mockGetReferenceList(Future.successful(chargeRefList))
          mockCreateSessionSimpleAssessment(Future successful createSessionDataResponse)
          val result =
            Await.result(
              sut.createSession(
                createSessionRequest.copy(amount = None, saUtr = None, taxType = Some(TaxTypeEnum.appSimpleAssessment), reference = Some(chargeRef1)),
                journeyId,
                sautrOpt = None,
                ninoOpt  = Some(saUtr.utr)
              ),
              0.5.seconds
            )
          result.sessionDataId.value shouldEqual "51cc67d6-21da-11ec-9621-0242ac130002"
        }
      }

      "amount = None, payload reference don't match auth reference" should {

        "throw exception: FailToMatchTaxIdOnAuth" in {
          mockGetReferenceList(Future.successful(chargeRefList))
          intercept[FailToMatchTaxIdOnAuth] {
            Await.result(
              sut.createSession(
                createSessionRequest.copy(amount = None, saUtr = None, taxType = Some(TaxTypeEnum.appSimpleAssessment), reference = Some("56765")),
                journeyId,
                sautrOpt = None,
                ninoOpt  = Some(saUtr.utr)
              ),
              0.5.seconds
            )
          }
        }
      }

      "amount in pence = None, payload reference  matches auth reference" should {

        "throw exception: MalformedRequestException" in {
          intercept[MalformedRequestException] {
            Await.result(
              sut.createSession(
                createSessionRequest.copy(amountInPence = None,
                                          saUtr         = None,
                                          taxType       = Some(TaxTypeEnum.appSimpleAssessment),
                                          reference     = Some(chargeRef1)
                                         ),
                journeyId,
                sautrOpt = None,
                ninoOpt  = Some(saUtr.utr)
              ),
              0.5.seconds
            )
          }
        }
      }

      "amount in pence is present , payload reference is None" should {

        "throw exception: MalformedRequestException" in {
          intercept[MalformedRequestException] {
            Await.result(
              sut.createSession(
                createSessionRequest.copy(
                  saUtr     = None,
                  taxType   = Some(TaxTypeEnum.appSimpleAssessment),
                  reference = None // set reference to None
                ),
                journeyId,
                sautrOpt = None,
                ninoOpt  = Some(saUtr.utr)
              ),
              0.5.seconds
            )
          }
        }
      }
    }

    "taxType == Blank" when {
      "Only amount and sautr are present && request sautr matches auth sautr" should {

        "Create a session" in {
          mockCreateSession(Future successful createSessionDataResponse)
          val result = Await.result(
            sut.createSession(createSessionRequest.copy(amount = Some(102.85), saUtr = Some(saUtr), taxType = None),
                              journeyId,
                              sautrOpt = Some(saUtr)
                             ),
            0.5.seconds
          )
          result.sessionDataId.value shouldEqual "51cc67d6-21da-11ec-9621-0242ac130002"
        }

      }

      "Only amount and sautr are present && request sautr don't matches auth sautr" should {

        "Throw exception : FailToMatchTaxIdOnAuth" in {

          intercept[FailToMatchTaxIdOnAuth] {
            Await.result(
              sut.createSession(createSessionRequest.copy(amount = Some(102.85), saUtr = Some(SaUtr("456789")), taxType = None),
                                journeyId,
                                sautrOpt = Some(saUtr)
                               ),
              0.5.seconds
            )
          }
        }

      }

      "Amount is missing and sautr is  present && request sautr  matches auth sautr" should {

        "Throw exception : MalformedRequestException" in {

          intercept[MalformedRequestException] {
            Await.result(
              sut.createSession(createSessionRequest.copy(amount = None, saUtr = Some(saUtr), taxType = None), journeyId, sautrOpt = Some(saUtr)),
              0.5.seconds
            )
          }
        }

      }

      "Amount is present and sautr is  missing " should {

        "Throw exception : MalformedRequestException" in {

          intercept[MalformedRequestException] {
            Await.result(
              sut.createSession(createSessionRequest.copy(amount = Some(102.85), saUtr = None, taxType = None), journeyId, sautrOpt = Some(saUtr)),
              0.5.seconds
            )
          }
        }

      }

    }
  }

  "when selectBank invoked and connector succeeds then" should {
    "return unit response" in {
      mockSelectBank(Future successful HttpResponse.apply(200, ""))

      val result: Unit = Await.result(sut.selectBank(sessionDataId, bankId, journeyId), 0.5.seconds)
      result shouldEqual ()
    }
  }

  "when selectBank invoked and connector fails then" should {
    "return an error" in {
      mockSelectBank(Future failed UpstreamErrorResponse("Error", 400, 400))

      intercept[UpstreamErrorResponse] {
        Await.result(sut.selectBank(sessionDataId, bankId, journeyId), 0.5.seconds)
      }
    }
  }

  "when initiatePayment invoked and connector succeeds then" should {
    "return payment session response" in {
      mockInitiatePayment(Future successful paymentInitiatedResponse)

      val result = Await.result(sut.initiatePayment(sessionDataId, journeyId), 0.5.seconds)
      result.paymentUrl shouldEqual paymentUrl
    }
  }

  "when initiatePayment invoked and connector fails then" should {
    "return an error" in {
      mockInitiatePayment(Future failed UpstreamErrorResponse("Error", 400, 400))

      intercept[UpstreamErrorResponse] {
        Await.result(sut.initiatePayment(sessionDataId, journeyId), 0.5.seconds)
      }
    }
  }

  "when getPaymentStatus invoked and connector getPaymentStatus returns success then" should {
    "return banks" in {
      mockPaymentStatus(Future successful paymentStatusOpenBankingResponse)

      val result = Await.result(sut.getPaymentStatus(sessionDataId, journeyId), 0.5.seconds)
      result.status shouldEqual "Verified"
    }
  }

  "when getPaymentStatus invoked and connector getPaymentStatus returns NotFoundException then" should {
    "return an error" in {
      mockPaymentStatus(Future failed UpstreamErrorResponse("Error", 400, 400))

      intercept[UpstreamErrorResponse] {
        await(sut.getPaymentStatus(sessionDataId, journeyId))
      }
    }
  }

  "when updatePayment invoked" should {
    "return a new payment session response and clear the payment" in {
      mockClearPayment(Future successful ())
      mockInitiatePayment(Future successful paymentInitiatedUpdateResponse)

      val result = Await.result(sut.updatePayment(sessionDataId, journeyId), 0.5.seconds)

      result.paymentUrl.toString() shouldEqual "https://some-updated-bank.com?param=dosomething"
    }
  }

  "when updatePayment invoked and initiate payment fails" should {
    "return an error" in {
      mockClearPayment(Future successful ())
      mockInitiatePayment(Future failed UpstreamErrorResponse("Error", 400, 400))
      intercept[UpstreamErrorResponse] {
        await(sut.updatePayment(sessionDataId, journeyId))
      }
    }
  }

  "when urlConsumed invoked and url consumed succeeds" should {
    Seq(true, false).foreach { consumed =>
      s"return $consumed" in {
        mockUrlConsumed(Future successful consumed)

        val result = Await.result(sut.urlConsumed(sessionDataId, journeyId), 0.5.seconds)
        result.consumed shouldEqual consumed
      }
    }
  }

  "when urlConsumed invoked and url consumed fails" should {
    "return an error" in {
      mockUrlConsumed(Future failed UpstreamErrorResponse("Error", 400, 400))
      intercept[UpstreamErrorResponse] {
        await(sut.urlConsumed(sessionDataId, journeyId))
      }
    }
  }

  "when getSession invoked and status is initiated and connector getSession returns success then" should {
    "return session" in {
      mockSession(Future successful sessionInitiatedDataResponse)

      val result = Await.result(sut.getSession(sessionDataId, journeyId), 0.5.seconds)
      result.sessionDataId shouldEqual "51cc67d6-21da-11ec-9621-0242ac130002"
      result.amountInPence shouldEqual BigDecimal.valueOf(12564)
      result.bankId        shouldEqual None
      result.state         shouldEqual "SessionInitiated"
      result.paymentDate   shouldEqual None
      result.reference     shouldEqual "CS700100AK"
    }
  }

  "when getSession invoked and status is bank selected and connector getSession returns success then" should {
    "return session" in {
      mockSession(Future successful sessionBankSelectedDataResponse)

      val result = Await.result(sut.getSession(sessionDataId, journeyId), 0.5.seconds)
      result.sessionDataId shouldEqual "51cc67d6-21da-11ec-9621-0242ac130002"
      result.amountInPence shouldEqual 12564
      result.bankId        shouldEqual Some("a-bank-id")
      result.state         shouldEqual "BankSelected"
      result.paymentDate   shouldEqual None
      result.reference     shouldEqual "CS700100AK"
    }
  }

  "when getSession invoked and status is payment finished and connector getSession returns success then" should {
    "return session" in {
      mockSession(Future successful sessionPaymentFinishedDataResponse)

      val result = Await.result(sut.getSession(sessionDataId, journeyId), 0.5.seconds)
      result.sessionDataId shouldEqual "51cc67d6-21da-11ec-9621-0242ac130002"
      result.amountInPence shouldEqual 12564
      result.bankId        shouldEqual Some("a-bank-id")
      result.state         shouldEqual "PaymentFinished"
      result.paymentDate   shouldEqual Some(LocalDate.now())
      result.reference     shouldEqual "CS700100AK"
    }
  }

  "when getSession invoked and status is payment finalised and connector getSession returns success then" should {
    "return session" in {
      mockSession(Future successful sessionPaymentFinalisedDataResponse)

      val result = Await.result(sut.getSession(sessionDataId, journeyId), 0.5.seconds)
      result.sessionDataId shouldEqual "51cc67d6-21da-11ec-9621-0242ac130002"
      result.amountInPence shouldEqual 12564
      result.bankId        shouldEqual Some("a-bank-id")
      result.state         shouldEqual "PaymentFinalised"
      result.paymentDate   shouldEqual Some(LocalDate.parse("2021-12-01"))
      result.reference     shouldEqual "CS700100AK"
    }
  }

  "when getSession invoked and connector fails then" should {
    "return an error" in {
      mockSession(Future failed UpstreamErrorResponse("Error", 400, 400))

      intercept[UpstreamErrorResponse] {
        await(sut.getSession(sessionDataId, journeyId))
      }
    }
  }

  private def mockBanks(future: Future[List[Bank]]): Unit =
    (mockConnector
      .getBanks(_: JourneyId)(_: HeaderCarrier))
      .expects(journeyId, hc)
      .returning(future)

  private def mockCreateSession(future: Future[CreateSessionDataResponse]): Unit =
    (mockConnector
      .createSession(_: BigDecimal, _: OriginSpecificData, _: JourneyId)(_: HeaderCarrier))
      .expects(amountInPence, selfAssessmentSpecificData, journeyId, hc)
      .returning(future)

  private def mockGetReferenceList(response: Future[List[String]]) = {
    (mockP800Service
      .getChargeRefernceList(_: Option[String], _: Int)(_: ExecutionContext, _: HeaderCarrier))
      .expects(*, *, *, *)
      .returning(response)
  }

  private def mockCreateSessionSimpleAssessment(future: Future[CreateSessionDataResponse]): Unit =
    (mockConnector
      .createSession(_: BigDecimal, _: OriginSpecificData, _: JourneyId)(_: HeaderCarrier))
      .expects(amountInPence, simpleAssessmentSpecificData, journeyId, hc)
      .returning(future)

  private def mockCreateSessionSelfAssessment(future: Future[CreateSessionDataResponse]): Unit =
    (mockConnector
      .createSession(_: BigDecimal, _: OriginSpecificData, _: JourneyId)(_: HeaderCarrier))
      .expects(amountInPence, selfAssessmentSpecificData, journeyId, hc)
      .returning(future)

  private def mockSelectBank(future: Future[HttpResponse]): Unit =
    (mockConnector
      .selectBank(_: String, _: String, _: JourneyId)(_: HeaderCarrier))
      .expects(sessionDataId, bankId, journeyId, hc)
      .returning(future)

  private def mockInitiatePayment(future: Future[InitiatePaymentResponse]): Unit =
    (mockConnector
      .initiatePayment(_: String, _: String, _: JourneyId)(_: HeaderCarrier))
      .expects(sessionDataId, returnUrl, journeyId, hc)
      .returning(future)

  private def mockPaymentStatus(future: Future[OpenBankingPaymentStatusResponse]): Unit =
    (mockConnector
      .getPaymentStatus(_: String, _: JourneyId)(_: HeaderCarrier))
      .expects(sessionDataId, journeyId, hc)
      .returning(future)

  private def mockUrlConsumed(future: Future[Boolean]): Unit =
    (mockConnector
      .urlConsumed(_: String, _: JourneyId)(_: HeaderCarrier))
      .expects(sessionDataId, journeyId, hc)
      .returning(future)

  private def mockClearPayment(future: Future[Unit]): Unit =
    (mockConnector
      .clearPayment(_: String, _: JourneyId)(_: HeaderCarrier))
      .expects(sessionDataId, journeyId, hc)
      .returning(future)

  private def mockSession(future: Future[SessionData[OriginSpecificSessionData]]): Unit =
    (mockConnector
      .getSession(_: String, _: JourneyId)(_: HeaderCarrier))
      .expects(sessionDataId, journeyId, hc)
      .returning(future)
}
