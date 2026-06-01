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

import com.google.inject.Inject
import play.api.Logging
import play.api.http.Status.{NOT_FOUND, OK}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mobilepayments.models.p800Response.PaymentResponse

import javax.inject.Named
import scala.concurrent.{ExecutionContext, Future}

class P800Connector @Inject() (
  http: HttpClientV2,
  @Named("onlinePaymentsBaseUrl") baseUrl: String
)(implicit ec: ExecutionContext)
    extends Logging {

  def getPaymentsData(
    nino: Option[String],
    taxYear: Int
  )(implicit hc: HeaderCarrier): Future[Option[PaymentResponse]] =
    http
      .get(url"$baseUrl/p800/${nino.getOrElse("")}/taxyear/$taxYear/payment-history")
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
      .map {
        case Right(httpResponse) =>
          httpResponse.status match {
            case OK =>
              val paymentsResponseData = httpResponse.json.as[PaymentResponse]
              Some(paymentsResponseData)
            case NOT_FOUND =>
              logger.warn(
                s"Not Found Response received from P800_Payments microservice response - ${httpResponse.body}"
              )
              None
            case _ =>
              logger.warn(
                s"Error found in Response received from P800_Payments microservice for NINO-$nino with status- ${httpResponse.status} And response body - ${httpResponse.body}"
              )
              throw new Exception(s"Error Retreiving data for NINO-$nino")
          }
        case Left(error) => throw error
      }
      .recover {
        case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND => None

        case e: Throwable =>
          logger.error(
            s"Problem occurred getting P800 Payments status for NINO : $nino"
          )
          throw e
      }

}
