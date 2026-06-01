package controllers

import play.api.libs.json.Json
import play.api.libs.ws.WSRequest
import stubs.AuthStub.*
import stubs.OpenBankingStub.*
import stubs.PayApiStub.*
import stubs.ShutteringStub.{stubForShutteringDisabled, stubForShutteringEnabled}
import uk.gov.hmrc.mobilepayments.MobilePaymentsTestData
import uk.gov.hmrc.mobilepayments.domain.dto.response.{LatestPaymentsResponse, PayByCardResponse, PaymentStatusResponse, UrlConsumedResponse}
import uk.gov.hmrc.mobilepayments.models.openBanking.response.InitiatePaymentResponse
import utils.BaseISpec
import play.api.libs.ws.writeableOf_JsValue
import stubs.P800Stub.stubForP800Response

import java.time.LocalDate

class LivePaymentControllerISpec extends BaseISpec with MobilePaymentsTestData {

  private val paymentUrl: String = "https://some-bank.com?param=dosomething"

  "POST /payments" should {
    "return 200 with payment url" in {
      grantAccess()
      stubForShutteringDisabled
      stubForInitiatePayment(response = paymentInitiatedResponseJson)
      stubForGetSession(response      = sessionDataBankSelectedResponseJson)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse("{}")))
      response.status shouldBe 200
      val parsedResponse = Json.parse(response.body).as[InitiatePaymentResponse]
      parsedResponse.paymentUrl.toString() shouldBe "https://some-bank.com?param=dosomething"
    }

    "return 500 when request from payment is malformed" in {
      grantAccess()
      stubForShutteringDisabled
      stubForInitiatePayment(response = rawMalformedJson)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse("{}")))
      response.status shouldBe 500
    }

    "return 401 when a 401 is returned from payment" in {
      grantAccess()
      stubForShutteringDisabled
      stubForInitiatePayment(401)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader)
      val response = await(request.post(Json.parse("{}")))
      response.status shouldBe 401
    }

    "return 404 when a 404 is returned from payment" in {
      grantAccess()
      stubForShutteringDisabled
      stubForInitiatePayment(404)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse("{}")))
      response.status shouldBe 404
    }

    "return 401 when auth fails" in {
      authorisationRejected()

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader)
      val response = await(request.post(Json.parse("{}")))
      response.status shouldBe 401
    }

    "return 500 when unknown error is returned from payment" in {
      grantAccess()
      stubForShutteringDisabled
      stubForInitiatePayment(500)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse("{}")))
      response.status shouldBe 500
    }

    "return 521 when shuttered" in {
      grantAccess()
      stubForShutteringEnabled

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse("{}")))
      response.status shouldBe 521
    }
  }

  "PUT /payments" should {
    "return 200 with the payment url" in {
      grantAccess()
      stubForShutteringDisabled
      stubForInitiatePayment(response = paymentInitiatedResponseJson)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.put(Json.obj("paymentUrl" -> paymentUrl)))
      response.status shouldBe 200
      val parsedResponse = Json.parse(response.body).as[InitiatePaymentResponse]
      parsedResponse.paymentUrl.toString() shouldBe paymentUrl
    }

    "return 500 when request from payment is malformed" in {
      grantAccess()
      stubForShutteringDisabled
      stubForClearPayment()
      stubForInitiatePayment(response = rawMalformedJson)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.put(Json.parse("{}")))
      response.status shouldBe 500
    }

    "return 404 when a 401 is returned from clear payment" in {
      grantAccess()
      stubForShutteringDisabled
      stubForClearPayment(401)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.put(Json.parse("{}")))
      response.status shouldBe 404
    }

    "return 401 when a 401 is returned from initiate payment" in {
      grantAccess()
      stubForShutteringDisabled
      stubForClearPayment()
      stubForInitiatePayment(401)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader)
      val response = await(request.put(Json.parse("{}")))
      response.status shouldBe 401
    }

    "return 404 when a 404 is returned from clear payment" in {
      grantAccess()
      stubForShutteringDisabled
      stubForClearPayment(404)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.put(Json.parse("{}")))
      response.status shouldBe 404
    }

    "return 404 when a 404 is returned from initiate payment" in {
      grantAccess()
      stubForShutteringDisabled
      stubForClearPayment()
      stubForInitiatePayment(404)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.put(Json.parse("{}")))
      response.status shouldBe 404
    }

    "return 401 when auth fails" in {
      authorisationRejected()

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader)
      val response = await(request.put(Json.parse("{}")))
      response.status shouldBe 401
    }

    "return 404 when unknown error 500 returned from clear payment" in {
      grantAccess()
      stubForShutteringDisabled
      stubForClearPayment(500)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.put(Json.parse("{}")))
      response.status shouldBe 404
    }

    "return 500 when unknown error 500 returned from initiate payment" in {
      grantAccess()
      stubForShutteringDisabled
      stubForClearPayment()
      stubForInitiatePayment(500)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.put(Json.parse("{}")))
      response.status shouldBe 500
    }

    "return 521 when shuttered" in {
      grantAccess()
      stubForShutteringEnabled

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.put(Json.parse("{}")))
      response.status shouldBe 521
    }
  }

  "GET /payments/:sessionDataId/url-consumed" should {
    Seq(true, false).foreach { consumed =>
      s"return 200 with the consumed flag equal to $consumed" in {
        grantAccess()
        stubForShutteringDisabled
        stubForUrlConsumed(response = Json.toJson(consumed).toString())

        val request: WSRequest = wsUrl(
          s"/payments/$sessionDataId/url-consumed?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
        val response = await(request.get())
        response.status shouldBe 200
        val parsedResponse = Json.parse(response.body).as[UrlConsumedResponse]
        parsedResponse.consumed shouldBe consumed
      }
    }

    "return 401 when a 401 is returned from url consumed" in {
      grantAccess()
      stubForShutteringDisabled
      stubForUrlConsumed(401)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId/url-consumed?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader)
      val response = await(request.get())
      response.status shouldBe 401
    }

    "return 404 when a 404 is returned from url consumed" in {
      grantAccess()
      stubForShutteringDisabled
      stubForUrlConsumed(404)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId/url-consumed?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 404
    }

    "return 401 when auth fails" in {
      authorisationRejected()

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId/url-consumed?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader)
      val response = await(request.get())
      response.status shouldBe 401
    }

    "return 500 when unknown error 500 returned from url consumed" in {
      grantAccess()
      stubForShutteringDisabled
      stubForUrlConsumed(500)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId/url-consumed?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 500
    }

    "return 521 when shuttered" in {
      grantAccess()
      stubForShutteringEnabled

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId/url-consumed?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 521
    }
  }

  "GET /payments" should {

    "return 200 with status and trigger sending of email if payment status Verified or Complete " in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPaymentStatus(response = paymentStatusResponseJson)
      stubForGetSession(response       = sessionDataPaymentFinalisedResponseJson)
      stubForSendEmail()
      stubForSetEmailSentFlag()

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 200
      val parsedResponse = Json.parse(response.body).as[PaymentStatusResponse]
      parsedResponse.status shouldEqual "Verified"
      verifyEmailSent(sessionDataId)
    }

    "return 200 with status and do not trigger sending of email if emailSent = true " in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPaymentStatus(response = paymentStatusResponseJson)
      stubForGetSession(response       = sessionDataPaymentFinalisedEmailSentResponseJson)
      stubForSendEmail()
      stubForSetEmailSentFlag()

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 200
      val parsedResponse = Json.parse(response.body).as[PaymentStatusResponse]
      parsedResponse.status shouldEqual "Verified"
      verifyEmailNotSend(sessionDataId)
    }

    "return 200 with status and origin is set to AppSimpleAssessment do not trigger sending of email if emailSent = true " in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPaymentStatus(response = paymentStatusResponseJson)
      stubForGetSession(response       = sessionDataPaymentFinalisedSimpleAssessmentResponseJson)
      stubForSendEmail()
      stubForSetEmailSentFlag()

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 200
      val parsedResponse = Json.parse(response.body).as[PaymentStatusResponse]
      parsedResponse.status shouldEqual "Verified"
      verifyEmailNotSend(sessionDataId)
    }

    "return 500 when response from status json is malformed" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPaymentStatus(response = rawMalformedJson)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 500
    }

    "return 401 when a 401 is returned from open-banking" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPaymentStatus(401)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader)
      val response = await(request.get())
      response.status shouldBe 401
    }

    "return 404 when a 404 is returned from open-banking" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPaymentStatus(404)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 404
    }

    "return 401 when auth fails" in {
      authorisationRejected()

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 401
    }

    "return 500 when unknown error is returned from open-banking" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPaymentStatus(500)

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 500
    }

    "return 521 when shuttered" in {
      grantAccess()
      stubForShutteringEnabled

      val request: WSRequest = wsUrl(
        s"/payments/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 521
    }
  }

  "POST /payments/latest-payments" should {
    "return 200 with the latest payments when taxType = appSelfAssessment" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPayments(200, paymentsResponseString())
      getUTRFromAuth("1122334455")
      getNinoFromAuth()

      val request: WSRequest = wsUrl(
        s"/payments/latest-payments?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse(latestPaymentsSelfAssessmentJson)))
      response.status shouldBe 200
      val parsedResponse = Json.parse(response.body).as[LatestPaymentsResponse]
      parsedResponse.payments.size               shouldBe 2
      parsedResponse.payments.head.date.toString shouldBe LocalDate.now().toString
      parsedResponse.payments.head.amountInPence shouldBe 11100

    }

    "return 401 with the latest payments when taxType = appSelfAssessment and auth utr don't matches req utr" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPayments(200, paymentsResponseString())
      getUTRFromAuth("1234567")
      getNinoFromAuth()

      val request: WSRequest = wsUrl(
        s"/payments/latest-payments?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse(latestPaymentsSelfAssessmentJson)))
      response.status shouldBe 401

    }

    "return 200 with the latest payments when taxType = appSimpleAssessment, auth ref matches request ref" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPayments(200, paymentsResponseString(), "other", "22441133")
      getUTRFromAuth("22441133")
      getNinoFromAuth()
      stubForP800Response(nino, taxYear, "22441133", chargeRef2)

      val request: WSRequest = wsUrl(
        s"/payments/latest-payments?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse(latestPaymentsSimpleAssessmentJson)))
      response.status shouldBe 200
      val parsedResponse = Json.parse(response.body).as[LatestPaymentsResponse]
      parsedResponse.payments.size               shouldBe 2
      parsedResponse.payments.head.date.toString shouldBe LocalDate.now().toString
      parsedResponse.payments.head.amountInPence shouldBe 11100

    }

    "return 401 with the latest payments when taxType = appSimpleAssessment and auth ref don;t match request ref" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPayments(200, paymentsResponseString(), "other", "22441133")
      getUTRFromAuth("22441133")
      getNinoFromAuth()
      stubForP800Response(nino, taxYear, chargeRef1, chargeRef2)

      val request: WSRequest = wsUrl(
        s"/payments/latest-payments?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse(latestPaymentsSimpleAssessmentJson)))
      response.status shouldBe 401

    }

    "return 404 when no valid payments returned" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPayments(200, paymentsResponseString(LocalDate.now().minusDays(15)))
      getUTRFromAuth("1122334455")
      getNinoFromAuth()
      val request: WSRequest = wsUrl(
        s"/payments/latest-payments?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse(latestPaymentsSelfAssessmentJson)))
      response.status shouldBe 404

    }

    "return 404 when a 404 is returned from get payments" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPayments(404)
      getUTRFromAuth("1122334455")
      getNinoFromAuth()
      val request: WSRequest = wsUrl(
        s"/payments/latest-payments?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse(latestPaymentsSelfAssessmentJson)))
      response.status shouldBe 404
    }

    "return 401 when auth fails" in {
      authorisationRejected()

      val request: WSRequest = wsUrl(
        s"/payments/latest-payments?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader)
      val response = await(request.post(Json.parse(latestPaymentsSelfAssessmentJson)))
      response.status shouldBe 401
    }

    "return 500 when a 401 is returned from get payments" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPayments(401)
      getUTRFromAuth("1122334455")
      getNinoFromAuth()

      val request: WSRequest = wsUrl(
        s"/payments/latest-payments?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse(latestPaymentsSelfAssessmentJson)))
      response.status shouldBe 500
    }

    "return 500 when unknown error 500 returned from get payments" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetPayments(500)
      getUTRFromAuth("1122334455")
      getNinoFromAuth()

      val request: WSRequest = wsUrl(
        s"/payments/latest-payments?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse(latestPaymentsSelfAssessmentJson)))
      response.status shouldBe 500
    }

    "return 521 when shuttered" in {
      grantAccess()
      stubForShutteringEnabled

      val request: WSRequest = wsUrl(
        s"/payments/latest-payments?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.parse(latestPaymentsSelfAssessmentJson)))
      response.status shouldBe 521
    }
  }

  "GET /payments/pay-by-card" should {
    "return 200 with the pay by card url without the domain prefix when the taxType is set to appSelfAssessment" in {
      grantAccess()
      stubForShutteringDisabled
      getNinoFromAuth()
      stubForPayByCard(200, payApiPayByCardResponseJson)
      getUTRFromAuth(utr)

      val request: WSRequest = wsUrl(
        s"/payments/pay-by-card?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
      val response =
        await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
      response.status shouldBe 200
      val parsedResponse = Json.parse(response.body).as[PayByCardResponse]
      parsedResponse.payByCardUrl shouldBe "/pay/initiate-journey?traceId=83303543"

    }

//    "return 200 with the pay by card url without the domain prefix when the taxType is set to appSimpleAssessment" in {
//      grantAccess()
//      stubForShutteringDisabled
//      getNinoFromAuth()
//      stubForPayByCardSimpleAssessment(200, payApiPayByCardResponseJson)
//
//      val request: WSRequest = wsUrl(
//        s"/payments/pay-by-card?journeyId=$journeyId"
//      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
//      val response = await(
//        request.post(
//          Json.obj("amountInPence" -> 100000, "taxType" -> "appSimpleAssessment", "taxYear" -> 2023, "reference" -> "12345678")
//        )
//      )
//      response.status shouldBe 200
//      val parsedResponse = Json.parse(response.body).as[PayByCardResponse]
//      parsedResponse.payByCardUrl shouldBe "/pay/initiate-journey?traceId=83303543"
//    }

//    "return 404 when a 404 is returned from payments" in {
//      grantAccess()
//      stubForShutteringDisabled
//      getNinoFromAuth()
//      stubForPayByCardSimpleAssessment(404)
//
//      val request: WSRequest = wsUrl(
//        s"/payments/pay-by-card?journeyId=$journeyId"
//      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
//      val response = await(
//        request.post(
//          Json.obj("amountInPence" -> 100000, "taxType" -> "appSimpleAssessment", "taxYear" -> 2023, "reference" -> utr)
//        )
//      )
//      response.status shouldBe 404
//    }

//    "return 401 when auth fails" in {
//      authorisationRejected()
//
//      val request: WSRequest = wsUrl(
//        s"/payments/pay-by-card?journeyId=$journeyId"
//      ).addHttpHeaders(acceptJsonHeader)
//      val response = await(
//        request.post(
//          Json.obj("amountInPence" -> 100000, "taxType" -> "appSimpleAssessment", "taxYear" -> 2023, "reference" -> utr)
//        )
//      )
//      response.status shouldBe 401
//    }

    "return 401 when a 401 is returned from payments" in {
      grantAccess()
      stubForShutteringDisabled
      getNinoFromAuth()
      stubForPayByCard(401)
      getUTRFromAuth(utr)

      val request: WSRequest = wsUrl(
        s"/payments/pay-by-card?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response =
        await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
      response.status shouldBe 401
    }

    "return 500 when unknown error 500 returned from payments" in {
      grantAccess()
      stubForShutteringDisabled
      getNinoFromAuth()
      stubForPayByCard(500)
      getUTRFromAuth(utr)

      val request: WSRequest = wsUrl(
        s"/payments/pay-by-card?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response =
        await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
      response.status shouldBe 500
    }

    "return 521 when shuttered" in {
      grantAccess()
      stubForShutteringEnabled

      val request: WSRequest = wsUrl(
        s"/payments/pay-by-card/?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response =
        await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
      response.status shouldBe 521
    }
  }
}
