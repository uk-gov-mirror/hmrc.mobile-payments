package stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import eu.timepit.refined.auto.*
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.{Assertion, Assertions}
import play.api.libs.json.Json
import sttp.model.StatusCode
import uk.gov.hmrc.mobilepayments.domain.types.JourneyId
import uk.gov.hmrc.mobilepayments.models.openBanking.request.InitiateEmailSendRequest
import uk.gov.hmrc.mobilepayments.models.p800Response.PaymentResponse

object P800Stub {

  def stubForP800Response(nino: Option[String], taxYear: Int, chargeRef1: String, chargeRef2: String, statusCode: Int = 200) = {
    stubFor(
      get(
        urlEqualTo(
          s"/p800/${nino.getOrElse("")}/taxyear/$taxYear/payment-history"
        )
      ).willReturn(
        aResponse()
          .withStatus(statusCode)
          .withBody(s"""
                       |{
                       |  "nino": "${nino.getOrElse("")}",
                       |  "taxYear" : "$taxYear",
                       |  "chargeReference":"$chargeRef1",
                       |  "previousTaxYear":{
                       |    "nino": "${nino.getOrElse("")}",
                       |    "taxYear" : "$taxYear",
                       |   "chargeReference":"$chargeRef2"
                       |  }
                       |}

                     """.stripMargin)
      )
    )

  }
}
