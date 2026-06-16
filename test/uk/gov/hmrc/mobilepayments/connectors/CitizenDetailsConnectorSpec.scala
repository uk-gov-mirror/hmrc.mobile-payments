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
import play.api.test.Helpers.await
import uk.gov.hmrc.domain.{Nino, SaUtr, TaxIds}
import uk.gov.hmrc.http.{NotFoundException, UpstreamErrorResponse}
import uk.gov.hmrc.mobilepayments.MobilePaymentsTestData
import uk.gov.hmrc.mobilepayments.common.BaseSpec
import uk.gov.hmrc.mobilepayments.mocks.ConnectorStub
import uk.gov.hmrc.mobilepayments.models.CidPerson

import scala.concurrent.Future

class CitizenDetailsConnectorSpec extends BaseSpec with ConnectorStub with MobilePaymentsTestData with ScalaFutures {
  val connector = new CitizenDetailsConnector(mockHttp, "https://serviceUrl")
  val nino1 = "AA000003D"

  val user: CidPerson = CidPerson(TaxIds(Set(Nino("AA000003D"), SaUtr("1097133333"))))

  "CitizenDetailsConnectorSpec" should {

    "return 200 and fetch UTR via Nino if present" in {
      performGET(Future successful user)
      val result = await(connector.getUtrByNino(nino1))
      result shouldBe Some(SaUtr("1097133333"))
    }

    "return None if citizen details api fail with Upstream error response of bad request" in {
      performGET(Future successful (Future.failed(UpstreamErrorResponse("bad requet", 400))))
      val result = await(connector.getUtrByNino(nino1))
      result shouldBe None
    }

    "return None if citizen details api fail with any Upstream error response" in {
      performGET(Future.failed(UpstreamErrorResponse("Server error", 500)))
      val result = await(connector.getUtrByNino(nino1))
      result shouldBe None
    }

    "throw exception, if the UTR is not found" in {
      performGET(Future.failed(UpstreamErrorResponse("UTR not found", 404)))
      val result = connector.getUtrByNino(nino1)
      intercept[NotFoundException] {
        await(result)
      }

    }
    "throw Not exception, if citizen details api throw not found exception" in {
      performGET(Future.failed(NotFoundException("UTR not found")))
      val result = connector.getUtrByNino(nino1)
      intercept[NotFoundException] {
        await(result)
      }

    }

  }

}
