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

import uk.gov.hmrc.http.{HeaderCarrier}
import uk.gov.hmrc.mobilepayments.MobilePaymentsTestData
import uk.gov.hmrc.mobilepayments.common.BaseSpec
import uk.gov.hmrc.mobilepayments.connectors.P800Connector
import uk.gov.hmrc.mobilepayments.models.p800Response.{PaymentResponse, PreviousTaxYear}

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class P800ServiceSpec extends BaseSpec with MobilePaymentsTestData {

  private val mockP800Connector: P800Connector = mock[P800Connector]
  private val p800Service: P800Service = new P800Service(mockP800Connector)

  def makePaymentResponse(ref1: Option[String], ref2: Option[String]) =
    PaymentResponse(nino = nino.get, taxYear = "2024", chargeReference = ref1, previousTaxYear = Some(PreviousTaxYear(nino.get, "2024", ref2)))

  "getChargeReferenceList" when {

    "connector returns a valid response" should {
      "return a list of charge references" in {
        mockGetPaymentsData(Future.successful(Some(makePaymentResponse(Some(chargeRef1), Some(chargeRef2)))))
        val result = Await.result(p800Service.getChargeRefernceList(nino, 2024), 0.5.seconds)
        result shouldBe chargeRefList
      }

    }

    "Connector returns None" should {
      "return a list of charge references" in {
        mockGetPaymentsData(Future.successful(None))
        val result = Await.result(p800Service.getChargeRefernceList(nino, 2024), 0.5.seconds)
        result shouldBe (List.empty)
      }

    }

    "connector returns only one value" should {
      "return a list of charge references" in {
        mockGetPaymentsData(Future.successful(Some(makePaymentResponse(Some(chargeRef1), None))))
        val result = Await.result(p800Service.getChargeRefernceList(nino, 2024), 0.5.seconds)
        result shouldBe List(chargeRef1)
      }

    }
  }

  private def mockGetPaymentsData(response: Future[Option[PaymentResponse]]) =
    (mockP800Connector
      .getPaymentsData(_: Option[String], _: Int)(_: HeaderCarrier))
      .expects(*, *, *)
      .returning(response)
}
