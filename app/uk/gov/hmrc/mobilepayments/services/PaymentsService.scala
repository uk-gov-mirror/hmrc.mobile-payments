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

package uk.gov.hmrc.mobilepayments.services

import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilepayments.connectors.PaymentsConnector
import uk.gov.hmrc.mobilepayments.controllers.errors.MalformedRequestException
import uk.gov.hmrc.mobilepayments.domain.dto.request.{PayByCardRequestGeneric, TaxTypeEnum}
import uk.gov.hmrc.mobilepayments.domain.{Payment, PaymentRecordListFromApi}
import uk.gov.hmrc.mobilepayments.domain.dto.response.{LatestPaymentsResponse, PayByCardResponse}
import uk.gov.hmrc.mobilepayments.domain.types.JourneyId
import uk.gov.hmrc.mobilepayments.models.payapi.PaymentStatuses.Successful
import cats.syntax.all.*
import play.api.Logger

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsService @Inject() (connector: PaymentsConnector) {

  val logger: Logger = Logger(this.getClass)

  def getLatestPayments(
    utr: Option[String],
    reference: Option[String],
    taxType: Option[TaxTypeEnum.Value],
    journeyId: JourneyId
  )(implicit executionContext: ExecutionContext, headerCarrier: HeaderCarrier): Future[Either[String, Option[LatestPaymentsResponse]]] = {
    (utr, reference) match {
      case (Some(sautr), Some(referenceValue)) =>
        if (sautr == referenceValue) {
          connector.getPayments(utr, reference, taxType, journeyId) map {
            case Right(payments) =>
              val recentPayments: List[Payment] =
                payments.map(paymentsList => filterPaymentsOlderThan14DaysOrUnsuccessful(paymentsList)).getOrElse(List.empty)
              if (recentPayments.isEmpty) Right(None)
              else
                Right(
                  Some(LatestPaymentsResponse.fromPayments(recentPayments))
                )

            case Right(None) => Right(None)
            case Left(e)     => Left(e)
            case _           => Left("Error calling pay-api")
          }
        } else {
          logger.info("Unauthorized! Reference in payload doesn't match with logged in UTR")
          Future.successful(Left("Unauthorized! Reference in payload doesn't match with logged in UTR"))
        }
      case _ =>
        logger.info("Unauthorized! UTR or reference is missing from payload/Enrolments")
        Future.successful(Left("Unauthorized! UTR or reference is missing from payload/Enrolments"))
    }

  }

  def getPayByCardUrl(
    request: PayByCardRequestGeneric,
    nino: Option[String],
    journeyId: JourneyId
  )(implicit executionContext: ExecutionContext, headerCarrier: HeaderCarrier): Future[PayByCardResponse] =
    request.taxType match {
      case TaxTypeEnum.appSelfAssessment =>
        connector
          .getPayByCardUrl(request.amountInPence, SaUtr(request.reference), journeyId)
          .map(response => PayByCardResponse(response.urlWithoutDomainPrefix))
      case TaxTypeEnum.appSimpleAssessment =>
        (request.reference, request.amountInPence, request.taxYear, nino) match {
          case (reference, amountInPence, Some(taxYear), Some(nino)) =>
            connector
              .getPayByCardUrlSimpleAssessment(amountInPence, nino, reference, taxYear, journeyId)
              .map(response => PayByCardResponse(response.urlWithoutDomainPrefix))
          case _ => throw new MalformedRequestException("Malformed Json: taxYear must also be provided")
        }
    }

  private def filterPaymentsOlderThan14DaysOrUnsuccessful(paymentsFromApi: PaymentRecordListFromApi) =
    paymentsFromApi.payments.filter(payment =>
      payment.createdOn.isAfter(LocalDate.now().minusDays(14).atStartOfDay()) && payment.status == Successful
    )

}
