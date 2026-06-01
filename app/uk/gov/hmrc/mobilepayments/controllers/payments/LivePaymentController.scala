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

package uk.gov.hmrc.mobilepayments.controllers.payments

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, BodyParser, ControllerComponents}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, InsufficientEnrolments}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.mobilepayments.controllers.ControllerChecks
import uk.gov.hmrc.mobilepayments.controllers.action.AccessControl
import uk.gov.hmrc.mobilepayments.controllers.errors.{ErrorHandling, JsonHandler}
import uk.gov.hmrc.mobilepayments.domain.dto.request.{LatestPaymentsRequest, PayByCardRequestGeneric}
import uk.gov.hmrc.mobilepayments.domain.types.JourneyId
import uk.gov.hmrc.mobilepayments.models.openBanking.ecospend.EcospendFinalStatuses.Completed
import uk.gov.hmrc.mobilepayments.models.openBanking.ecospend.EcospendFinishedStatuses.Verified
import uk.gov.hmrc.mobilepayments.models.openBanking.response.InitiatePaymentResponse
import uk.gov.hmrc.mobilepayments.services.{AuditService, OpenBankingService, PaymentsService, ShutteringService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter.fromRequest

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class LivePaymentController @Inject() (
  override val authConnector: AuthConnector,
  @Named("controllers.confidenceLevel") override val confLevel: Int,
  cc: ControllerComponents,
  openBankingService: OpenBankingService,
  shutteringService: ShutteringService,
  auditService: AuditService,
  paymentsService: PaymentsService
)(implicit val executionContext: ExecutionContext)
    extends BackendController(cc)
    with PaymentController
    with AccessControl
    with ControllerChecks
    with ErrorHandling
    with JsonHandler {

  override def parser: BodyParser[AnyContent] = controllerComponents.parsers.anyContent

  override val app: String = "Payment-Controller"

  def createPayment(
    sessionDataId: String,
    journeyId: JourneyId
  ): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            for {
              response <- openBankingService.initiatePayment(sessionDataId, journeyId)
              session  <- openBankingService.getSession(sessionDataId, journeyId)
              _        <- auditService.sendPaymentEvent(session, journeyId.value)
            } yield Ok(Json.toJson[InitiatePaymentResponse](response))
          }
        }
      }
    }

  def updatePayment(
    sessionDataId: String,
    journeyId: JourneyId
  ): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            openBankingService
              .updatePayment(
                sessionDataId,
                journeyId
              )
              .map { response =>
                Ok(Json.toJson[InitiatePaymentResponse](response))
              }
          }
        }
      }
    }

  def urlConsumed(
    sessionDataId: String,
    journeyId: JourneyId
  ): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            openBankingService
              .urlConsumed(
                sessionDataId,
                journeyId
              )
              .map { response =>
                Ok(Json.toJson(response))
              }
          }
        }
      }
    }

  def getPaymentStatus(
    sessionDataId: String,
    journeyId: JourneyId
  ): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            for {
              response <- openBankingService.getPaymentStatus(sessionDataId, journeyId)
              session  <- openBankingService.getSession(sessionDataId, journeyId)
            } yield {
              if (
                (response.status == Verified.toString || response.status == Completed.toString) &&
                !session.emailSent
                  .getOrElse(false)
              ) {
                openBankingService.sendEmail(sessionDataId, journeyId, session.origin)
              }
              Ok(Json.toJson(response))
            }
          }
        }
      }
    }

  def latestPayments(journeyId: JourneyId): Action[JsValue] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async(parse.json) { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            withValidJson[LatestPaymentsRequest] { latestPaymentsRequest =>
              getNinoFromAuth.flatMap { nino => // checking for auth NINO
                getSaUTRFromAuth.flatMap { sautrOpt => // checking for auth sautr
                  paymentsService
                    .getLatestPayments(nino,
                                       sautrOpt.map(_.utr),
                                       Some(latestPaymentsRequest.reference),
                                       Some(latestPaymentsRequest.taxType),
                                       journeyId
                                      ) map {
                    case Right(None)                 => NotFound
                    case Right(payments)             => Ok(Json.toJson(payments))
                    case Left(s"Unauthorized! $msg") => Unauthorized(s"Unauthorized!")
                    case Left(e)                     => InternalServerError(e)
                  }
                }
              }
            }
          }
        }
      }
    }

  def getPayByCardURL(journeyId: JourneyId): Action[JsValue] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async(parse.json) { implicit request =>
      implicit val hc: HeaderCarrier =
        fromRequest(request).copy(sessionId = Some(SessionId(journeyId.value)))
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            withValidJson[PayByCardRequestGeneric] { payByCardRequest =>
              getNinoFromAuth.flatMap { nino =>
                getSaUTRFromAuth.flatMap { sautrOpt =>
                  paymentsService
                    .getPayByCardUrl(
                      payByCardRequest,
                      nino,
                      sautrOpt,
                      journeyId
                    )
                    .map(response => Ok(Json.toJson(response)))
                }
              }
            }
          }
        }
      }
    }

}
