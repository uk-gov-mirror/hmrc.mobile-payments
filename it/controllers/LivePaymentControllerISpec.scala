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
import stubs.CidStub.getStubToFetchUtrViaNino
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

  "POST /payments/latest-payments" when {
    val malformedSAJson =
      s"""
         |{
         |"taxType": "appSelfAssessment"
         |}
         |""".stripMargin

    val malformedSimpleJson =
      s"""
         |{
         |"taxType": "appSimpleAssessment"
         |}
         |""".stripMargin

    val malformedJson =
      s"""
         |{
         |"reference": "12344"
         |}
         |""".stripMargin
    "taxType = appSelfAssessment" should {

      "return 200, if user has IR-SA enrolment and auth utr == payload reference" in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth(isSaActive = true)
        stubForGetPayments(200, paymentsResponseString())

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

      "return 200, if user has IR-SA And MTD enrolments  and auth utr == payload reference" in {
        grantAccess()
        stubForShutteringDisabled
        stubForGetPayments(200, paymentsResponseString())
        getNinoAndUTRFromAuth(isSaActive = true, isMtdActive = true)

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

      "return 200, if user has only MTD enrolments ,utr is fetched via cid and fetched utr == payload reference" in {
        grantAccess()
        stubForShutteringDisabled
        stubForGetPayments(200, paymentsResponseString())
        getNinoAndNOUtrInRetreivals(isMtdActive = true)
        getStubToFetchUtrViaNino(nino.get, utr)

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

      "return 200, if user has only MTD enrolments ,but utr is there in retrievals and fetched utr == payload reference" in {
        grantAccess()
        stubForShutteringDisabled
        stubForGetPayments(200, paymentsResponseString())
        getNinoAndUTRFromAuth(isMtdActive = true)
        getStubToFetchUtrViaNino(nino.get, utr)

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

      "return 401, if user has IR-SA enrolment and auth utr != payload reference " in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth(saUtr = "12345", isSaActive = true)
        stubForGetPayments(200, paymentsResponseString())

        val request: WSRequest = wsUrl(
          s"/payments/latest-payments?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.parse(latestPaymentsSelfAssessmentJson)))
        response.status shouldBe 401

      }

      "return 401, if user has IR-SA enrolment and no utr fetched  " in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth(saUtr = "", isSaActive = true)
        stubForGetPayments(200, paymentsResponseString())

        val request: WSRequest = wsUrl(
          s"/payments/latest-payments?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.parse(latestPaymentsSelfAssessmentJson)))
        response.status shouldBe 401

      }

      "return 401, if user has MTD enrolment only   and no utr fetched via cid connector " in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndNOUtrInRetreivals(isMtdActive = true)
        getStubToFetchUtrViaNino(nino.get, "")
        stubForGetPayments(200, paymentsResponseString())

        val request: WSRequest = wsUrl(
          s"/payments/latest-payments?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.parse(latestPaymentsSelfAssessmentJson)))
        response.status shouldBe 401

      }

      "return 404, if no valid payment is returned" in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth(isSaActive = true)
        stubForGetPayments(200, paymentsResponseString(LocalDate.now().minusDays(15)))

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
        getNinoAndUTRFromAuth(isSaActive = true)
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

      "return 500 when unknown error 500 returned from get payments" in {
        grantAccess()
        stubForShutteringDisabled
        stubForGetPayments(500)
        getNinoAndUTRFromAuth(isSaActive = true)

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

      "return 400, bad request when reference is missing from the request" in {
        grantAccess()
        stubForShutteringEnabled

        val request: WSRequest = wsUrl(
          s"/payments/latest-payments?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.parse(malformedSAJson)))
        response.status shouldBe 521
      }
    }

    "taxType = appSimpleAssessment" should {

      "return 200 with the latest payments, auth ref matches request ref" in {
        grantAccess()
        stubForShutteringDisabled
        stubForGetPayments(200, paymentsResponseString(), "other", "22441133")
        getNinoAndUTRFromAuth()
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

      "return 401 with the latest payments  auth ref don;t match request ref" in {
        grantAccess()
        stubForShutteringDisabled
        stubForGetPayments(200, paymentsResponseString(), "other", "22441133")
        getNinoAndUTRFromAuth()
        stubForP800Response(nino, taxYear, chargeRef1, chargeRef2)

        val request: WSRequest = wsUrl(
          s"/payments/latest-payments?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.parse(latestPaymentsSimpleAssessmentJson)))
        response.status shouldBe 401

      }

      "return 404, if no valid payment is returned" in {
        grantAccess()
        stubForShutteringDisabled
        stubForGetPayments(200, paymentsResponseString(LocalDate.now().minusDays(15)))
        getNinoAndUTRFromAuth()
        stubForP800Response(nino, taxYear, "22441133", chargeRef2)

        val request: WSRequest = wsUrl(
          s"/payments/latest-payments?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.parse(latestPaymentsSimpleAssessmentJson)))
        response.status shouldBe 404
      }

      "return 404,  when a 404 is returned from get payments service" in {
        grantAccess()
        stubForShutteringDisabled
        stubForGetPayments(404, taxType = "other", reference = "22441133")
        getNinoAndUTRFromAuth()
        stubForP800Response(nino, taxYear, "22441133", chargeRef2)

        val request: WSRequest = wsUrl(
          s"/payments/latest-payments?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.parse(latestPaymentsSimpleAssessmentJson)))
        response.status shouldBe 404
      }

      "return 401 when auth fails" in {
        authorisationRejected()

        val request: WSRequest = wsUrl(
          s"/payments/latest-payments?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader)
        val response = await(request.post(Json.parse(latestPaymentsSimpleAssessmentJson)))
        response.status shouldBe 401
      }

      "return 500 when unknown error 500 returned from get payments" in {
        grantAccess()
        stubForShutteringDisabled
        stubForGetPayments(500, taxType = "other", reference = "22441133")
        getNinoAndUTRFromAuth()
        stubForP800Response(nino, taxYear, "22441133", chargeRef2)

        val request: WSRequest = wsUrl(
          s"/payments/latest-payments?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.parse(latestPaymentsSimpleAssessmentJson)))
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

      "return 400, bad request when reference is missing from the request" in {
        grantAccess()
        stubForShutteringDisabled

        val request: WSRequest = wsUrl(
          s"/payments/latest-payments?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.parse(malformedSimpleJson)))
        response.status shouldBe 400
      }

    }

    "taxType = BLANK" should {

      "400, bad request  as malformed json" in {
        grantAccess()
        stubForShutteringDisabled

        val request: WSRequest = wsUrl(
          s"/payments/latest-payments?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.parse(malformedJson)))
        response.status shouldBe 400
      }
    }
  }

  "GET /payments/pay-by-card" when {

    "type = appSelfAssessment" should {

      "return 200, if user has IR-SA enrolment and auth utr == payload reference" in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth(isSaActive = true)
        stubForPayByCard(200, payApiPayByCardResponseJson)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
        response.status shouldBe 200
        val parsedResponse = Json.parse(response.body).as[PayByCardResponse]
        parsedResponse.payByCardUrl shouldBe "/pay/initiate-journey?traceId=83303543"
      }

      "return 200, if user has IR-SA And MTD enrolments  and auth utr == payload reference" in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth(isSaActive = true, isMtdActive = true)
        stubForPayByCard(200, payApiPayByCardResponseJson)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
        response.status shouldBe 200
        val parsedResponse = Json.parse(response.body).as[PayByCardResponse]
        parsedResponse.payByCardUrl shouldBe "/pay/initiate-journey?traceId=83303543"
      }

      "return 200, if user has only MTD enrolments ,utr is fetched via cid and fetched utr == payload reference" in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndNOUtrInRetreivals(isMtdActive = true)
        getStubToFetchUtrViaNino(nino.get, utr)
        stubForPayByCard(200, payApiPayByCardResponseJson)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
        response.status shouldBe 200
        val parsedResponse = Json.parse(response.body).as[PayByCardResponse]
        parsedResponse.payByCardUrl shouldBe "/pay/initiate-journey?traceId=83303543"
      }

      "return 200, if user has only MTD enrolments ,but utr is there in retrievals and fetched utr == payload reference" in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth(isMtdActive = true)
        getStubToFetchUtrViaNino(nino.get, utr)
        stubForPayByCard(200, payApiPayByCardResponseJson)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
        response.status shouldBe 200
        val parsedResponse = Json.parse(response.body).as[PayByCardResponse]
        parsedResponse.payByCardUrl shouldBe "/pay/initiate-journey?traceId=83303543"
      }

      "return 401, if user has has IR-SA enrolment and auth utr != payload reference" in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth(saUtr = "12345", isSaActive = true)
        stubForPayByCard(200, payApiPayByCardResponseJson)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
        response.status shouldBe 401
      }

      "return 401, if user has IR-SA enrolment and no utr fetched " in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth(saUtr = "", isSaActive = true)
        stubForPayByCard(200, payApiPayByCardResponseJson)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
        response.status shouldBe 401
      }

      "return 401, if user has MTD enrolment only and no utr fetched via cid connector " in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndNOUtrInRetreivals(isMtdActive = true)
        getStubToFetchUtrViaNino(nino.get, "")
        stubForPayByCard(200, payApiPayByCardResponseJson)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
        response.status shouldBe 401
      }

      "return 404, if service return 404" in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth(isSaActive = true)
        stubForPayByCard(404)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
        response.status shouldBe 404
      }

      "return 401, if auth fails" in {
        authorisationRejected()

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
        response.status shouldBe 401
      }

      "return 500 when unknown error 500 returned from get payments" in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth(isSaActive = true)
        stubForPayByCard(500)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
        response.status shouldBe 500
      }

      "return 521 when shuttered" in {
        grantAccess()
        stubForShutteringEnabled

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment", "reference" -> utr)))
        response.status shouldBe 521
      }

      "return 400, bad request " when {

        "reference is missing from the request" in {
          grantAccess()
          stubForShutteringDisabled

          val request: WSRequest = wsUrl(
            s"/payments/pay-by-card?journeyId=$journeyId"
          ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
          val response =
            await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "appSelfAssessment")))
          response.status shouldBe 400
        }

        "amountInPence is missing from the request" in {
          grantAccess()
          stubForShutteringDisabled

          val request: WSRequest = wsUrl(
            s"/payments/pay-by-card?journeyId=$journeyId"
          ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
          val response =
            await(request.post(Json.obj("taxType" -> "appSelfAssessment", "reference" -> utr)))
          response.status shouldBe 400
        }

      }

    }

    "type = appSimpleAssessment" should {

      "return 200, if request reference == logged in use charge reference" in {

        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth()
        stubForP800Response(nino, taxYear, "12345678", chargeRef2)
        stubForPayByCardSimpleAssessment(200, payApiPayByCardResponseJson)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response = await(
          request.post(
            Json.obj("amountInPence" -> 100000, "taxType" -> "appSimpleAssessment", "taxYear" -> 2023, "reference" -> "12345678")
          )
        )
        response.status shouldBe 200
        val parsedResponse = Json.parse(response.body).as[PayByCardResponse]
        parsedResponse.payByCardUrl shouldBe "/pay/initiate-journey?traceId=83303543"
      }

      "return 401 , if auth charge ref != request ref" in {

        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth()
        stubForP800Response(nino, taxYear, "11223344", chargeRef2)
        stubForPayByCardSimpleAssessment(200, payApiPayByCardResponseJson)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response = await(
          request.post(
            Json.obj("amountInPence" -> 100000, "taxType" -> "appSimpleAssessment", "taxYear" -> 2023, "reference" -> "12345678")
          )
        )
        response.status shouldBe 401
      }

      "return 401, if auth fails" in {
        authorisationRejected()

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response = await(
          request.post(
            Json.obj("amountInPence" -> 100000, "taxType" -> "appSimpleAssessment", "taxYear" -> 2023, "reference" -> "12345678")
          )
        )
        response.status shouldBe 401
      }

      "return 404, if service return 404" in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth()
        stubForP800Response(nino, taxYear, "12345678", chargeRef2)
        stubForPayByCardSimpleAssessment(404)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response = await(
          request.post(
            Json.obj("amountInPence" -> 100000, "taxType" -> "appSimpleAssessment", "taxYear" -> 2023, "reference" -> "12345678")
          )
        )
        response.status shouldBe 404
      }

      "return 500 when unknown error 500 returned from  payments wesrvice" in {
        grantAccess()
        stubForShutteringDisabled
        getNinoAndUTRFromAuth()
        stubForP800Response(nino, taxYear, "12345678", chargeRef2)
        stubForPayByCardSimpleAssessment(500)

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response = await(
          request.post(
            Json.obj("amountInPence" -> 100000, "taxType" -> "appSimpleAssessment", "taxYear" -> 2023, "reference" -> "12345678")
          )
        )
        response.status shouldBe 500
      }

      "return 521 when shuttered" in {
        grantAccess()
        stubForShutteringEnabled

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response = await(
          request.post(
            Json.obj("amountInPence" -> 100000, "taxType" -> "appSimpleAssessment", "taxYear" -> 2023, "reference" -> "12345678")
          )
        )
        response.status shouldBe 521
      }

      "return 400, bad request " when {

        "reference is missing from the request" in {
          grantAccess()
          stubForShutteringDisabled

          val request: WSRequest = wsUrl(
            s"/payments/pay-by-card?journeyId=$journeyId"
          ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)

          val response = await(
            request.post(
              Json.obj("amountInPence" -> 100000, "taxType" -> "appSimpleAssessment", "taxYear" -> 2024)
            )
          )
          response.status shouldBe 400
        }

        "amountInPence is missing from the request" in {
          grantAccess()
          stubForShutteringDisabled

          val request: WSRequest = wsUrl(
            s"/payments/pay-by-card?journeyId=$journeyId"
          ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)

          val response = await(
            request.post(
              Json.obj("taxType" -> "appSimpleAssessment", "taxYear" -> 2024, "reference" -> utr)
            )
          )
          response.status shouldBe 400
        }
      }
    }

    "type = BLANK" should {

      "return 400, bad request ,malformed json" in {

        grantAccess()
        stubForShutteringDisabled

        val request: WSRequest = wsUrl(
          s"/payments/pay-by-card?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader, sessionIdHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 100000, "taxType" -> "", "reference" -> "123456")))
        response.status shouldBe 400

      }
    }
  }
}
