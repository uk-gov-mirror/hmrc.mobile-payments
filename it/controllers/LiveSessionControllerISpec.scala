package controllers

import play.api.libs.json.Json
import play.api.libs.ws.WSRequest
import stubs.AuthStub.*
import stubs.OpenBankingStub.*
import stubs.ShutteringStub.{stubForShutteringDisabled, stubForShutteringEnabled}
import uk.gov.hmrc.mobilepayments.MobilePaymentsTestData
import uk.gov.hmrc.mobilepayments.domain.dto.response.SessionDataResponse
import utils.BaseISpec
import play.api.libs.ws.writeableOf_JsValue
import stubs.P800Stub.stubForP800Response
import uk.gov.hmrc.mobilepayments.models.openBanking.response.CreateSessionDataResponse

import java.time.LocalDate

class LiveSessionControllerISpec extends BaseISpec with MobilePaymentsTestData {

  "POST /sessions" should {
    "return 200 when payload is valid and taxType not supplied" in {
      grantAccess()
      stubForShutteringDisabled
      stubForCreateSession(response = createSessionDataResponseJson)
      getUTRFromAuth()
      getNinoFromAuth()

      val request: WSRequest = wsUrl(
        s"/sessions?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.obj("amount" -> 1200, "saUtr" -> "CS700100A")))
      response.status shouldBe 200
      val parsedResponse = Json.parse(response.body).as[CreateSessionDataResponse]
      parsedResponse.sessionDataId.value shouldBe "51cc67d6-21da-11ec-9621-0242ac130002"
    }

    "return 200 when payload is valid and the taxType is set to appSelfAssessment" in {
      grantAccess()
      stubForShutteringDisabled
      stubForCreateSession(response = createSessionDataResponseJson)
      getUTRFromAuth()
      getNinoFromAuth()

      val request: WSRequest = wsUrl(
        s"/sessions?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(
        request.post(Json.obj("amountInPence" -> 1200, "reference" -> "CS700100A", "taxType" -> "appSelfAssessment"))
      )
      response.status shouldBe 200
      val parsedResponse = Json.parse(response.body).as[CreateSessionDataResponse]
      parsedResponse.sessionDataId.value shouldBe "51cc67d6-21da-11ec-9621-0242ac130002"
    }

    "return 401 when taxType is set to appSelfAssessment and reference don;t match auth utr" in {
      grantAccess()
      stubForShutteringDisabled
      stubForCreateSession(response = createSessionDataResponseJson)
      getUTRFromAuth()
      getNinoFromAuth()

      val request: WSRequest = wsUrl(
        s"/sessions?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(
        request.post(Json.obj("amountInPence" -> 1200, "reference" -> "1234", "taxType" -> "appSelfAssessment"))
      )
      response.status shouldBe 401

    }

    "return 200 when payload is valid and the taxType is set to appSimpleAssessment" in {
      grantAccess()
      stubForShutteringDisabled
      stubForCreateSession(response = createSessionDataResponseJson)
      getUTRFromAuth()
      getNinoFromAuth()
      stubForP800Response(nino, taxYear, chargeRef1, chargeRef2)

      val request: WSRequest = wsUrl(
        s"/sessions?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(
        request.post(Json.obj("amountInPence" -> 1200, "reference" -> chargeRef1, "taxType" -> "appSimpleAssessment"))
      )
      response.status shouldBe 200
      val parsedResponse = Json.parse(response.body).as[CreateSessionDataResponse]
      parsedResponse.sessionDataId.value shouldBe "51cc67d6-21da-11ec-9621-0242ac130002"
    }

    "return 401 when  taxType = appSimpleAssessment but request reference don't match auth reference" in {
      grantAccess()
      stubForShutteringDisabled
      getUTRFromAuth()
      getNinoFromAuth()
      stubForP800Response(nino, taxYear, chargeRef1, chargeRef2)

      val request: WSRequest = wsUrl(
        s"/sessions?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(
        request.post(
          Json.obj("amountInPence" -> 1200,
                   "reference" ->
                     "1234",
                   "taxType" -> "appSimpleAssessment"
                  )
        )
      )
      response.status shouldBe 401

    }

    "return 500 when response from session is malformed" in {
      grantAccess()
      stubForShutteringDisabled
      stubForCreateSession(response = rawMalformedJson)
      getUTRFromAuth()
      getNinoFromAuth()

      val request: WSRequest = wsUrl(
        s"/sessions?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.obj("amount" -> 1098.3, "saUtr" -> "CS700100A")))
      response.status shouldBe 500
    }

    "return 401 when a 401 is returned from session" in {
      grantAccess()
      stubForShutteringDisabled
      stubForCreateSession(401)
      getUTRFromAuth()
      getNinoFromAuth()
      val request: WSRequest = wsUrl(
        s"/sessions?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader)
      val response = await(request.post(Json.obj("amount" -> 1200, "saUtr" -> "CS700100A")))
      response.status shouldBe 401
    }

    "return 404 when a 404 is returned from session" in {
      grantAccess()
      stubForShutteringDisabled
      stubForCreateSession(404)
      getUTRFromAuth()
      getNinoFromAuth()

      val request: WSRequest = wsUrl(
        s"/sessions?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.obj("amount" -> 1200, "saUtr" -> "CS700100A")))
      response.status shouldBe 404
    }

    "return 401 when auth fails" in {
      authorisationRejected()

      val request: WSRequest = wsUrl(
        s"/sessions?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader)
      val response = await(request.post(Json.obj("amount" -> 1200, "saUtr" -> "CS700100A")))
      response.status shouldBe 401
    }

    "return 500 when unknown error is returned from session" in {
      grantAccess()
      stubForShutteringDisabled
      stubForCreateSession(500)
      getUTRFromAuth()
      getNinoFromAuth()

      val request: WSRequest = wsUrl(
        s"/sessions?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.obj("amount" -> 1200, "saUtr" -> "CS700100A")))
      response.status shouldBe 500
    }

    "return 521 when shuttered" in {
      grantAccess()
      stubForShutteringEnabled

      val request: WSRequest = wsUrl(
        s"/sessions?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.obj("amount" -> 1200, "saUtr" -> "CS700100A")))
      response.status shouldBe 521
    }
  }

  "GET /sessions/:sessionDataId" should {
    "return 200 when payload is valid" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetSession(response = sessionDataPaymentFinalisedResponseJson)

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 200
      val parsedResponse = Json.parse(response.body).as[SessionDataResponse]
      parsedResponse.sessionDataId shouldEqual sessionDataId
      parsedResponse.amountInPence shouldEqual 12564
      parsedResponse.bankId        shouldEqual Some("a-bank-id")
      parsedResponse.paymentDate   shouldEqual Some(LocalDate.parse("2021-12-01"))
      parsedResponse.reference     shouldEqual "CS700100AK"
      parsedResponse.email.get     shouldEqual "test@test.com"
    }

    "return 500 when request from session is malformed" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetSession(response = rawMalformedJson)

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 500
    }

    "return 401 when a 401 is returned from session" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetSession(401)

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 401
    }

    "return 404 when a 404 is returned from session" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetSession(404)

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 404
    }

    "return 401 when auth fails" in {
      authorisationRejected()

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader)
      val response = await(request.get())
      response.status shouldBe 401
    }

    "return 500 when unknown error is returned from session" in {
      grantAccess()
      stubForShutteringDisabled
      stubForGetSession(500)

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 500
    }

    "return 521 when shuttered" in {
      grantAccess()
      stubForShutteringEnabled

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.get())
      response.status shouldBe 521
    }
  }

  "POST /set-email" should {
    "return 201" in {
      grantAccess()
      stubForShutteringDisabled
      stubForSetEmail(response = Json.obj("email" -> "test@test.com").toString())

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId/set-email?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.obj("email" -> "test@test.com")))
      response.status shouldBe 201
    }

    "return 401 when auth fails" in {
      authorisationRejected()

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId/set-email?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader)
      val response = await(request.post(Json.obj("email" -> "test@test.com")))
      response.status shouldBe 401
    }

    "return 521 when shuttered" in {
      grantAccess()
      stubForShutteringEnabled

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId/set-email?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.obj("email" -> "test@test.com")))
      response.status shouldBe 521
    }
  }

  "POST /set-future-date" should {
    "return 201" in {
      grantAccess()
      stubForShutteringDisabled
      stubForSetFutureDate(response = Json.obj("maybeFutureDate" -> "2024-02-28").toString())

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId/set-future-date?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.obj("maybeFutureDate" -> "2024-02-28")))
      response.status shouldBe 201
    }

    "return 401 when auth fails" in {
      authorisationRejected()

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId/set-future-date?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader)
      val response = await(request.post(Json.obj("maybeFutureDate" -> "2024-02-28")))
      response.status shouldBe 401
    }

    "return 521 when shuttered" in {
      grantAccess()
      stubForShutteringEnabled

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId/set-future-date?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
      val response = await(request.post(Json.obj("maybeFutureDate" -> "2024-02-28")))
      response.status shouldBe 521
    }
  }

  "DELETE /sessions/:sessionDataId/clear-email" should {
    "return 204 when call is successful" in {
      grantAccess()
      stubForShutteringDisabled
      stubForClearEmail()

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId/clear-email?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.delete())
      response.status shouldBe 204
    }

    "return 401 when auth fails" in {
      authorisationRejected()

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId/clear-email?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader)
      val response = await(request.delete())
      response.status shouldBe 401
    }

    "return 521 when shuttered" in {
      grantAccess()
      stubForShutteringEnabled

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId/clear-email?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.delete())
      response.status shouldBe 521
    }
  }

  "DELETE /sessions/:sessionDataId/clear-future-date" should {
    "return 204 when call is successful" in {
      grantAccess()
      stubForShutteringDisabled
      stubForClearFutureDate()

      val request: WSRequest = wsUrl(s"/sessions/$sessionDataId/clear-future-date?journeyId=$journeyId")
        .addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.delete())
      response.status shouldBe 204
    }

    "return 401 when auth fails" in {
      authorisationRejected()

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId/clear-future-date?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader)
      val response = await(request.delete())

      response.status shouldBe 401
    }

    "return 521 when shuttered" in {
      grantAccess()
      stubForShutteringEnabled

      val request: WSRequest = wsUrl(
        s"/sessions/$sessionDataId/clear-future-date?journeyId=$journeyId"
      ).addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
      val response = await(request.delete())
      response.status shouldBe 521
    }
  }

}
