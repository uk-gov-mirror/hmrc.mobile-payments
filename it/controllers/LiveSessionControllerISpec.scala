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
import stubs.CidStub.*
import stubs.P800Stub.stubForP800Response
import uk.gov.hmrc.mobilepayments.models.openBanking.response.CreateSessionDataResponse

import java.time.LocalDate

class LiveSessionControllerISpec extends BaseISpec with MobilePaymentsTestData {

  "POST /sessions" when {
    val utr: String = "1122334455"
    "taxType = appSelfAssessment" should {

      "return 200, if user has IR-SA enrolment and auth utr == payload reference == request sautr" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response    = createSessionDataResponseJson)
        getNinoAndUTRFromAuth(isSaActive = true)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> utr, "taxType" -> "appSelfAssessment")))
        response.status shouldBe 200
        val parsedResponse = Json.parse(response.body).as[CreateSessionDataResponse]
        parsedResponse.sessionDataId.value shouldBe "51cc67d6-21da-11ec-9621-0242ac130002"
      }

      "return 200, if user has IR-SA And MTD enrolments  and auth utr == payload reference == request sautr" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response    = createSessionDataResponseJson)
        getNinoAndUTRFromAuth(isSaActive = true, isMtdActive = true)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> utr, "taxType" -> "appSelfAssessment")))
        response.status shouldBe 200
        val parsedResponse = Json.parse(response.body).as[CreateSessionDataResponse]
        parsedResponse.sessionDataId.value shouldBe "51cc67d6-21da-11ec-9621-0242ac130002"
      }

      "return 200, if user has only MTD enrolments ,utr is fetched via cid and fetched utr == payload reference = request sautr" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response           = createSessionDataResponseJson)
        getNinoAndNOUtrInRetreivals(isMtdActive = true)
        getStubToFetchUtrViaNino(nino.get, utr)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> utr, "taxType" -> "appSelfAssessment")))
        response.status shouldBe 200
        val parsedResponse = Json.parse(response.body).as[CreateSessionDataResponse]
        parsedResponse.sessionDataId.value shouldBe "51cc67d6-21da-11ec-9621-0242ac130002"
      }

      "return 200, if user has only MTD enrolments ,but utr is there in retrievals and fetched utr == payload reference" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response     = createSessionDataResponseJson)
        getNinoAndUTRFromAuth(isMtdActive = true)
        getStubToFetchUtrViaNino(nino.get, utr)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> utr, "taxType" -> "appSelfAssessment")))
        response.status shouldBe 200
        val parsedResponse = Json.parse(response.body).as[CreateSessionDataResponse]
        parsedResponse.sessionDataId.value shouldBe "51cc67d6-21da-11ec-9621-0242ac130002"
      }

      "return 401, if user has any enrolment , but request sautr != request reference" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response    = createSessionDataResponseJson)
        getNinoAndUTRFromAuth(isSaActive = true, isMtdActive = true)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> utr, "taxType" -> "appSelfAssessment", "saUtr" -> "123")))
        response.status shouldBe 401
      }

      "return 401, if user has any enrolment and  request sautr == request reference, but != auth sautr" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response    = createSessionDataResponseJson)
        getNinoAndUTRFromAuth(isSaActive = true, isMtdActive = true, saUtr = "12344321")

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> utr, "taxType" -> "appSelfAssessment", "saUtr" -> utr)))
        response.status shouldBe 401
      }

      "return 401, if user has any enrolment and auth sautr != request reference, but matches request saUtr" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response    = createSessionDataResponseJson)
        getNinoAndUTRFromAuth(isSaActive = true, isMtdActive = true)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> "1234321", "taxType" -> "appSelfAssessment", "saUtr" -> utr)))
        response.status shouldBe 401
      }

      "return 401 , if no utr is fetched via auth call for IR-SA enrolemnt" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response    = createSessionDataResponseJson)
        getNinoAndUTRFromAuth(isSaActive = true, saUtr = "")

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> utr, "taxType" -> "appSelfAssessment", "saUtr" -> utr)))
        response.status shouldBe 401
      }

      "return 401, if user has MTD enrolment only and no utr fetched via cid connector " in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response           = createSessionDataResponseJson)
        getNinoAndNOUtrInRetreivals(isMtdActive = true)
        getStubToFetchUtrViaNino(nino.get, "")

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> utr, "taxType" -> "appSelfAssessment", "saUtr" -> utr)))
        response.status shouldBe 401
      }

      "return 404, iof service return 404" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(401)
        getNinoAndNOUtrInRetreivals(isSaActive = true)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> utr, "taxType" -> "appSelfAssessment", "saUtr" -> utr)))
        response.status shouldBe 401
      }

      "return 401, if auth fails" in {
        authorisationRejected()
        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response =
          await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> utr, "taxType" -> "appSelfAssessment", "saUtr" -> utr)))
        response.status shouldBe 401
      }

      "return 500 when service returns 5XX" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(500)
        getNinoAndUTRFromAuth(isSaActive = true)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> utr, "taxType" -> "appSelfAssessment")))
        response.status shouldBe 500
      }

      "return 521 when shuttered" in {
        grantAccess()
        stubForShutteringEnabled

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.obj("amountInPence" -> 1200, "reference" -> utr, "taxType" -> "appSelfAssessment")))
        response.status shouldBe 521
      }

      "return 400, bad request " when {

        "amountInPence is missing" in {
          grantAccess()
          stubForShutteringDisabled
          getNinoAndUTRFromAuth(isSaActive = true)

          val request: WSRequest = wsUrl(
            s"/sessions?journeyId=$journeyId"
          ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
          val response = await(request.post(Json.obj("reference" -> utr, "taxType" -> "appSelfAssessment")))
          response.status shouldBe 400
        }
        "reference is missing" in {
          grantAccess()
          stubForShutteringDisabled
          stubForCreateSession(response    = createSessionDataResponseJson)
          getNinoAndUTRFromAuth(isSaActive = true)

          val request: WSRequest = wsUrl(
            s"/sessions?journeyId=$journeyId"
          ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
          val response = await(request.post(Json.obj("amountInPence" -> 1200, "taxType" -> "appSelfAssessment")))
          response.status shouldBe 400
        }
      }
    }

    "taxType = appSimpleAssessment" should {

      "return 200, if request reference == logged in use charge reference " in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response = createSessionDataResponseJson)
        getNinoAndUTRFromAuth()
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

      "return 401 , if auth charge ref != request ref" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response = createSessionDataResponseJson)
        getNinoAndUTRFromAuth()
        stubForP800Response(nino, taxYear, "12345", chargeRef2)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(
          request.post(Json.obj("amountInPence" -> 1200, "reference" -> chargeRef1, "taxType" -> "appSimpleAssessment"))
        )
        response.status shouldBe 401
      }

      "return 401 if the service returns 401" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(401)
        getNinoAndUTRFromAuth()
        stubForP800Response(nino, taxYear, chargeRef1, chargeRef2)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(
          request.post(Json.obj("amountInPence" -> 1200, "reference" -> chargeRef1, "taxType" -> "appSimpleAssessment"))
        )
        response.status shouldBe 401
      }

      "return 401, if auth fails" in {
        authorisationRejected()

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(
          request.post(Json.obj("amountInPence" -> 1200, "reference" -> chargeRef1, "taxType" -> "appSimpleAssessment"))
        )
        response.status shouldBe 401
      }

      "return 404, if service return 404" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(404)
        getNinoAndUTRFromAuth()
        stubForP800Response(nino, taxYear, chargeRef1, chargeRef2)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(
          request.post(Json.obj("amountInPence" -> 1200, "reference" -> chargeRef1, "taxType" -> "appSimpleAssessment"))
        )
        response.status shouldBe 404
      }

      "return 500, if service returns 500" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(500)
        getNinoAndUTRFromAuth()
        stubForP800Response(nino, taxYear, chargeRef1, chargeRef2)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(
          request.post(Json.obj("amountInPence" -> 1200, "reference" -> chargeRef1, "taxType" -> "appSimpleAssessment"))
        )
        response.status shouldBe 500
      }

      "return 521, if service is shuttered" in {
        grantAccess()
        stubForShutteringEnabled

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(
          request.post(Json.obj("amountInPence" -> 1200, "reference" -> chargeRef1, "taxType" -> "appSimpleAssessment"))
        )
        response.status shouldBe 521
      }

      "return 400, bad request " when {

        "reference is missing from the request" in {
          grantAccess()
          stubForShutteringDisabled
          getNinoAndUTRFromAuth()
          stubForP800Response(nino, taxYear, chargeRef1, chargeRef2)

          val request: WSRequest = wsUrl(
            s"/sessions?journeyId=$journeyId"
          ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
          val response = await(
            request.post(Json.obj("amountInPence" -> 1200, "taxType" -> "appSimpleAssessment"))
          )
          response.status shouldBe 400

        }

        "amountInPence is missing from the request" in {
          grantAccess()
          stubForShutteringDisabled
          getNinoAndUTRFromAuth()
          stubForP800Response(nino, taxYear, chargeRef1, chargeRef2)

          val request: WSRequest = wsUrl(
            s"/sessions?journeyId=$journeyId"
          ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
          val response = await(
            request.post(Json.obj("reference" -> chargeRef1, "taxType" -> "appSimpleAssessment"))
          )
          response.status shouldBe 400

        }
      }
    }

    "taxType = not present" should {

      "return 200 and create session for self assessment payment, if amount and sautr are present and sautr == auth sautr" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response    = createSessionDataResponseJson)
        getNinoAndUTRFromAuth(isSaActive = true)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.obj("amount" -> 1200, "saUtr" -> utr)))
        response.status shouldBe 200
        val parsedResponse = Json.parse(response.body).as[CreateSessionDataResponse]
        parsedResponse.sessionDataId.value shouldBe "51cc67d6-21da-11ec-9621-0242ac130002"
      }

      "return 200 and create session for self assessment payment, if amount and sautr are present and sautr == auth sautr and reference !=sautr" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response    = createSessionDataResponseJson)
        getNinoAndUTRFromAuth(isSaActive = true)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.obj("amount" -> 1200, "saUtr" -> utr, "reference" -> "123432")))
        response.status shouldBe 200
        val parsedResponse = Json.parse(response.body).as[CreateSessionDataResponse]
        parsedResponse.sessionDataId.value shouldBe "51cc67d6-21da-11ec-9621-0242ac130002"
      }

      "return 401, if sautr != auth sautr" in {
        grantAccess()
        stubForShutteringDisabled
        stubForCreateSession(response    = createSessionDataResponseJson)
        getNinoAndUTRFromAuth(isSaActive = true)

        val request: WSRequest = wsUrl(
          s"/sessions?journeyId=$journeyId"
        ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
        val response = await(request.post(Json.obj("amount" -> 1200, "saUtr" -> "123432")))
        response.status shouldBe 401
      }
      "return 400, bad request" when {
        "amount is missing" in {
          grantAccess()
          stubForShutteringDisabled
          getNinoAndUTRFromAuth(isSaActive = true)

          val request: WSRequest = wsUrl(
            s"/sessions?journeyId=$journeyId"
          ).addHttpHeaders(acceptJsonHeader, contentHeader, authorisationJsonHeader)
          val response = await(request.post(Json.obj("saUtr" -> "123432")))
          response.status shouldBe 400
        }
      }
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
