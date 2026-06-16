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
import uk.gov.hmrc.mobilepayments.connectors.{P800Connector, PaymentsConnector}
import uk.gov.hmrc.mobilepayments.controllers.errors.{FailToMatchTaxIdOnAuth, MalformedRequestException}
import uk.gov.hmrc.mobilepayments.domain.dto.request.{PayByCardRequestGeneric, TaxTypeEnum}
import uk.gov.hmrc.mobilepayments.domain.{Payment, PaymentRecordListFromApi}
import uk.gov.hmrc.mobilepayments.domain.dto.response.{LatestPaymentsResponse, PayByCardResponse}
import uk.gov.hmrc.mobilepayments.domain.types.JourneyId
import uk.gov.hmrc.mobilepayments.models.payapi.PaymentStatuses.Successful
import play.api.Logger
import uk.gov.hmrc.time.TaxYear

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsService @Inject() (connector: PaymentsConnector, p800Service: P800Service) {

  val logger: Logger = Logger(this.getClass)
  val previousTaxYear: Int = TaxYear.current.previous.startYear

  def getLatestPayments(
    ninoOpt: Option[String],
    utr: Option[String],
    reference: Option[String],
    taxType: Option[TaxTypeEnum.Value],
    journeyId: JourneyId
  )(implicit executionContext: ExecutionContext, headerCarrier: HeaderCarrier): Future[Either[String, Option[LatestPaymentsResponse]]] = {

    (ninoOpt, utr, reference, taxType) match {
      case (_, Some(sautr), Some(referenceValue), Some(TaxTypeEnum.appSelfAssessment)) =>
        if (sautr == referenceValue) { // check if the auth sautr equals reference in case of appSelfAssessment
          getPayments(utr, reference, taxType, journeyId)
        } else {
          logger.info("Unauthorized! Reference in payload doesn't match with logged in UTR")
          Future.successful(Left("Unauthorized! Reference in payload doesn't match with logged in UTR"))
        }

      case (ninoOpt, _, Some(referenceValue), Some(TaxTypeEnum.appSimpleAssessment)) =>
        p800Service // calling p800 service to fetch the list of charge reference for T and T-1 yrs in case of appSimpleAssessment
          .getChargeRefernceList(ninoOpt, previousTaxYear)
          .flatMap { chargeRefList =>
            if (chargeRefList.contains(referenceValue)) { // Comparing the charge ref fetched in the list against the reference in payload
              getPayments(None, reference, taxType, journeyId)
            } else {
              // if charge ref is not a match then respond with the Unauthorised message
              logger.info("Unauthorized! Reference in payload doesn't match with Charge Reference of the user")
              Future.successful(Left("Unauthorized! Reference in payload doesn't match with Charge Reference of the user"))
            }
          }
      // case when no UTR found via cid call for MTD enrolment
      case (_, None, Some(referenceValue), Some(TaxTypeEnum.appSelfAssessment)) => Future.successful(Left("Unauthorized! UTR not found"))
      case _ =>
        logger.info("Malformed json")
        Future.successful(Left("Malformed json"))
    }

  }

  private def getPayments(utr: Option[String], reference: Option[String], taxType: Option[TaxTypeEnum.Value], journeyId: JourneyId)(implicit
    executionContext: ExecutionContext,
    headerCarrier: HeaderCarrier
  ): Future[Either[String, Option[LatestPaymentsResponse]]] = {
    connector.getPayments(utr, reference, taxType, journeyId) map {
      case Right(payments) =>
        val recentPayments: List[Payment] =
          payments.map(paymentsList => filterPaymentsOlderThan14DaysOrUnsuccessful(paymentsList)).getOrElse(List.empty)
        if (recentPayments.isEmpty) Right(None)
        else
          Right(
            Some(LatestPaymentsResponse.fromPayments(recentPayments))
          )
      case Left(e) =>
        Left(e)
    }
  }

  def getPayByCardUrl(
    request: PayByCardRequestGeneric,
    nino: Option[String] = None,
    sautrOpt: Option[SaUtr] = None,
    journeyId: JourneyId
  )(implicit executionContext: ExecutionContext, headerCarrier: HeaderCarrier): Future[PayByCardResponse] =
    request.taxType match {
      case TaxTypeEnum.appSelfAssessment =>
        if (sautrOpt.exists(_.utr == request.reference)) { // For appSelfAssessment- check if auth sautr equals reference
          connector
            .getPayByCardUrl(request.amountInPence, SaUtr(request.reference), journeyId)
            .map(response => PayByCardResponse(response.urlWithoutDomainPrefix))
        } else {
          throw new FailToMatchTaxIdOnAuth
        }

      case TaxTypeEnum.appSimpleAssessment =>
        (request.reference, request.amountInPence, request.taxYear, nino) match {
          case (reference, amountInPence, Some(taxYear), Some(nino)) =>
            p800Service.getChargeRefernceList(Some(nino), previousTaxYear).flatMap {
              referenceList => // For appSimpleAssessment- Fetch charge reference list for logged-in user
                if (referenceList.contains(reference)) { // Check if request reference is there in the above list
                  connector
                    .getPayByCardUrlSimpleAssessment(amountInPence, nino, reference, taxYear, journeyId)
                    .map(response => PayByCardResponse(response.urlWithoutDomainPrefix))
                } else {
                  throw new FailToMatchTaxIdOnAuth
                }
            }
          case _ =>
            logger.warn(s"Malformed JSON:: TaxYear  is Missing")
            throw new MalformedRequestException("Malformed JSON:: TaxYear is Missing")

        }

    }

  private def filterPaymentsOlderThan14DaysOrUnsuccessful(paymentsFromApi: PaymentRecordListFromApi) =
    paymentsFromApi.payments.filter(payment =>
      payment.createdOn.isAfter(LocalDate.now().minusDays(14).atStartOfDay()) && payment.status == Successful
    )

}
