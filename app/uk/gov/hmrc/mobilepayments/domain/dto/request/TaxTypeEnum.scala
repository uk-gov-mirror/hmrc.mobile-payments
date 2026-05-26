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

package uk.gov.hmrc.mobilepayments.domain.dto.request

import play.api.libs.json.*

object TaxTypeEnum extends Enumeration {

  val appSimpleAssessment: TaxTypeEnum.Value = TaxTypeEnum.Value("appSimpleAssessment")
  val appSelfAssessment: TaxTypeEnum.Value = TaxTypeEnum.Value("appSelfAssessment")

  implicit val format: Format[TaxTypeEnum.Value] = new Format[TaxTypeEnum.Value] {

    override def writes(o: TaxTypeEnum.Value): JsValue =
      JsString(o.toString)

    override def reads(json: JsValue): JsResult[TaxTypeEnum.Value] =
      json.as[String] match {
        //  case "appSimpleAssessment" => JsSuccess(appSimpleAssessment)
        case "appSelfAssessment" => JsSuccess(appSelfAssessment)
        case e                   => JsError(s"$e not recognised")
      }
  }
}
