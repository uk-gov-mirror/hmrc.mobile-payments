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

import play.api.Logging
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.mobilepayments.models.CidPerson

import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}

class CitizenDetailsConnector @Inject() (
  val http: HttpClientV2,
  @Named("citizen-details") serviceUrl: String
)(implicit ec: ExecutionContext)
    extends Logging {

  def getUtrByNino(nino: String)(implicit hc: HeaderCarrier): Future[Option[SaUtr]] = {
    http
      .get(url"$serviceUrl/citizen-details/nino/$nino")
      .execute[CidPerson]
      .map(_.ids.saUtr)
      .recover {
        case e: UpstreamErrorResponse if e.statusCode == BAD_REQUEST =>
          logger.info(s"Call to CID failed - Nino is invalid: $nino.")
          None
        case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND =>
          logger.info(s"Call to CID failed - No record for the Nino: $nino found on CID.")
          throw new NotFoundException("No UTR found on CID")
        case e: NotFoundException =>
          logger.info(s"Call to CID failed - No record for the Nino: $nino found on CID.")
          throw e
        case e: UpstreamErrorResponse =>
          logger.info(s"Call to CID failed $e")
          None
        case ex =>
          logger.info(s"Call to CID failed $ex")
          None
      }
  }

}
