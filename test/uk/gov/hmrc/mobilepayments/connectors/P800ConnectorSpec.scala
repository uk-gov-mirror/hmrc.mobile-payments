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

package uk.gov.hmrc.mobilepayments.connectors

import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status.*
import uk.gov.hmrc.http.{BadRequestException, HttpResponse}
import uk.gov.hmrc.mobilepayments.MobilePaymentsTestData
import uk.gov.hmrc.mobilepayments.mocks.ConnectorStub
import uk.gov.hmrc.time.TaxYear
import play.api.test.Helpers.await
import uk.gov.hmrc.mobilepayments.common.BaseSpec
import uk.gov.hmrc.mobilepayments.models.p800Response.PaymentResponse

import scala.concurrent.Future

class P800ConnectorSpec extends BaseSpec with ConnectorStub with MobilePaymentsTestData with ScalaFutures {

  val conn = new P800Connector(mockHttp, "https://p800-payments-url")
  val previousTaxYear: Int = TaxYear.current.previous.startYear

  "getPaymentsData" should {

    "return a valid payment response when receiving a NINO and tax year" in {
      performGET(Future.successful(Right(HttpResponse(OK, getTestJson(paymentDataResponseTestJson, nino.get), Map("" -> Seq(""))))))

      val result = await(conn.getPaymentsData(nino, previousTaxYear))
      result shouldBe Some(getTestJson(paymentDataResponseTestJson, nino.get).as[PaymentResponse])
    }

    "return None when 404 returned from payments service" in {
      performGET(
        Future.successful(Right(HttpResponse(NOT_FOUND, "", Map("" -> Seq("")))))
      )
      val result = await(conn.getPaymentsData(nino, previousTaxYear))
      result shouldBe None
    }

    "return Exception when 429 returned from payments service" in {
      performGET(
        Future.successful(Right(HttpResponse(TOO_MANY_REQUESTS, "", Map("" -> Seq("")))))
      )
      intercept[Exception] {
        await(conn.getPaymentsData(nino, previousTaxYear))
      }
    }

    "return an error" in {
      performGET(Future failed new BadRequestException("Bad Request"))
      intercept[BadRequestException] {
        await(conn.getPaymentsData(nino, previousTaxYear))
      }
    }

  }
}
