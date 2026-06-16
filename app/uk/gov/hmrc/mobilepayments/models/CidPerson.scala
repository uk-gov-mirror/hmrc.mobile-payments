/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.mobilepayments.models

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.domain.{Nino, SerialisableTaxId, TaxIds}

case class CidPerson(ids: TaxIds)

object CidPerson {
  implicit val taxIdsFormat: Format[TaxIds] = TaxIdsFormat.formattableTaxIds
  implicit val formats: OFormat[CidPerson] = Json.using[Json.WithDefaultValues].format[CidPerson]
}

object TaxIdsFormat {

  import uk.gov.hmrc.domain.TaxIds.*

  private def ninoBuilder(value: String): Nino =
    if (Nino.isValid(value)) Nino(value) else throw InvalidNinoException(value: String)

  private val ninoSerialiser = SerialisableTaxId("nino", ninoBuilder)
  private val saUtrSerialiser = defaultSerialisableIds.filter(id => Set("sautr").contains(id.taxIdName))
  private val saUtrAndNinoSerialiser = saUtrSerialiser :+ ninoSerialiser
  val formattableTaxIds: Format[TaxIds] = format(saUtrAndNinoSerialiser*)
  val formattableTaxIdsWithNoNino: Format[TaxIds] = format(saUtrSerialiser*)
}

case class InvalidNinoException(value: String) extends Exception(s"Invalid NINO value: $value")
