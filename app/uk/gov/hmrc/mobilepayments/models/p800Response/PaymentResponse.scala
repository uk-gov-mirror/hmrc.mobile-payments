/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.mobilepayments.models.p800Response

import play.api.Logging
import play.api.libs.json.{Format, Json}

case class PaymentResponse(nino: String, taxYear: String, chargeReference: Option[String] = None, previousTaxYear: Option[PreviousTaxYear] = None)

object PaymentResponse {
  implicit val formats: Format[PaymentResponse] = Json.format[PaymentResponse]
}

case class PreviousTaxYear(nino: String, taxYear: String, chargeReference: Option[String] = None)

object PreviousTaxYear {
  implicit val formats: Format[PreviousTaxYear] = Json.format[PreviousTaxYear]
}
