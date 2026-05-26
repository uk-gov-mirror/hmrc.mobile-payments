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

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.Json.obj
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilepayments.domain.dto.request.TaxTypeEnum
import uk.gov.hmrc.mobilepayments.domain.dto.response.Origins.{AppSa, AppSimpleAssessment}
import uk.gov.hmrc.mobilepayments.domain.dto.response.SessionDataResponse
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import javax.inject.Named
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuditService @Inject() (auditConnector: AuditConnector, @Named("appName") val appName: String) {

  def sendPaymentEvent(
    sessionData: SessionDataResponse,
    journeyId: String
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[AuditResult] = {
    val taxType: TaxTypeEnum.Value = if (sessionData.origin == AppSa) TaxTypeEnum.appSelfAssessment else TaxTypeEnum.appSimpleAssessment
    auditConnector.sendExtendedEvent(
      ExtendedDataEvent(
        appName,
        AuditService.paymentEventName,
        tags = hc.toAuditTags(AuditService.transactionName, AuditService.paymentPath),
        detail = obj(
          AuditService.journeyIdKey -> journeyId,
          if (sessionData.origin == AppSa) AuditService.utrKey -> sessionData.reference.toString
          else AuditService.utrKey                             -> "",
          if (sessionData.origin == AppSimpleAssessment)
            AuditService.p302ChargeRefKey    -> sessionData.reference.toString
          else AuditService.p302ChargeRefKey -> "",
          AuditService.amountKey  -> sessionData.amountInPence,
          AuditService.taxTypeKey -> taxType,
          AuditService.bankKey    -> sessionData.bankId.getOrElse("").toString
        )
      )
    )
  }

  object AuditService {
    val paymentPath = "/payment"
    val paymentEventName = "initiateOpenBankingPayment"
    val transactionName = "mobile-initiate-open-banking-payment"
    val amountKey = "amount"
    val utrKey = "utr"
    val journeyIdKey = "journeyId"
    val taxTypeKey = "taxType"
    val p302ChargeRefKey = "p302ChargeReference"
    val bankKey = "bank"
  }
}
