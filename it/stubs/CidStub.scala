package stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.matchers.should.Matchers.*

object CidStub {

  def getStubToFetchUtrViaNino(nino: String, utr: String, statusCode: Int = 200) = {
    stubFor(
      get(
        urlEqualTo(
          s"/citizen-details/nino/$nino"
        )
      ).willReturn(
        aResponse()
          .withStatus(statusCode)
          .withBody(s"""
                       |{
                       |  "ids": {"sautr" : "$utr"}
                       |}
                     """.stripMargin)
      )
    )
  }

}
