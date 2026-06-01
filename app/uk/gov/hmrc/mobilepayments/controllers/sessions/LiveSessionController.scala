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

package uk.gov.hmrc.mobilepayments.controllers.sessions

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, BodyParser, ControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilepayments.controllers.ControllerChecks
import uk.gov.hmrc.mobilepayments.controllers.action.AccessControl
import uk.gov.hmrc.mobilepayments.controllers.errors.{ErrorHandling, JsonHandler}
import uk.gov.hmrc.mobilepayments.domain.dto.request.{CreateSessionRequest, SetEmailRequest, SetFutureDateRequest}
import uk.gov.hmrc.mobilepayments.domain.types.JourneyId
import uk.gov.hmrc.mobilepayments.models.openBanking.response.CreateSessionDataResponse
import uk.gov.hmrc.mobilepayments.services.{OpenBankingService, ShutteringService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter.fromRequest

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.ExecutionContext

@Singleton()
class LiveSessionController @Inject() (
  override val authConnector: AuthConnector,
  @Named("controllers.confidenceLevel") override val confLevel: Int,
  cc: ControllerComponents,
  openBankingService: OpenBankingService,
  shutteringService: ShutteringService
)(implicit val executionContext: ExecutionContext)
    extends BackendController(cc)
    with SessionController
    with AccessControl
    with ControllerChecks
    with ErrorHandling
    with JsonHandler {

  override def parser: BodyParser[AnyContent] = controllerComponents.parsers.anyContent
  override val app: String = "Session-Controller"

  override def createSession(journeyId: JourneyId): Action[JsValue] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async(parse.json) { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            withValidJson[CreateSessionRequest] { createPaymentRequest =>
              getSaUTRFromAuth.flatMap { sautrOpt =>
                val updatedCreatePaymentRequest =
                  if (createPaymentRequest.saUtr.isEmpty) createPaymentRequest.copy(saUtr = sautrOpt)
                  else
                    createPaymentRequest // check if request have sautr or not. If present, keep the request else put auth sautr in request behind the scene
                getNinoFromAuth.flatMap { ninoOpt =>
                  openBankingService
                    .createSession(
                      updatedCreatePaymentRequest,
                      journeyId,
                      ninoOpt,
                      sautrOpt
                    )
                    .map(response => Ok(Json.toJson[CreateSessionDataResponse](response)))
                }

              }

            }
          }
        }
      }
    }

  override def getSession(
    sessionDataId: String,
    journeyId: JourneyId
  ): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            openBankingService
              .getSession(
                sessionDataId,
                journeyId
              )
              .map(response => Ok(Json.toJson(response)))
          }
        }
      }
    }

  override def setEmail(
    sessionDataId: String,
    journeyId: JourneyId
  ): Action[JsValue] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async(parse.json) { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            withValidJson[SetEmailRequest] { setEmailRequest =>
              openBankingService
                .setEmail(
                  sessionDataId,
                  setEmailRequest.email,
                  journeyId
                )
                .map(_ => Created)
            }
          }
        }
      }
    }

  override def setFutureDate(
    sessionDataId: String,
    journeyId: JourneyId
  ): Action[JsValue] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async(parse.json) { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            withValidJson[SetFutureDateRequest] { setFutureDateRequest =>
              openBankingService
                .setFutureDate(
                  sessionDataId,
                  setFutureDateRequest.maybeFutureDate,
                  journeyId
                )
                .map(_ => Created)
            }
          }
        }
      }
    }

  override def clearFutureDate(
    sessionDataId: String,
    journeyId: JourneyId
  ): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            openBankingService
              .clearFutureDate(
                sessionDataId,
                journeyId
              )
              .map(_ => NoContent)
          }
        }
      }
    }

  override def clearEmail(
    sessionDataId: String,
    journeyId: JourneyId
  ): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            openBankingService
              .clearEmail(
                sessionDataId,
                journeyId
              )
              .map { _ =>
                NoContent
              }
          }
        }
      }
    }
}
