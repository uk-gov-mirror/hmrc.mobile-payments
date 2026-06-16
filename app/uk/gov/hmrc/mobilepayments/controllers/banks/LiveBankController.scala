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

package uk.gov.hmrc.mobilepayments.controllers.banks

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, BodyParser, ControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilepayments.connectors.CitizenDetailsConnector
import uk.gov.hmrc.mobilepayments.controllers.ControllerChecks
import uk.gov.hmrc.mobilepayments.controllers.action.AccessControl
import uk.gov.hmrc.mobilepayments.controllers.errors.{ErrorHandling, JsonHandler}
import uk.gov.hmrc.mobilepayments.domain.dto.request.SelectBankRequest
import uk.gov.hmrc.mobilepayments.domain.types.JourneyId
import uk.gov.hmrc.mobilepayments.services.{OpenBankingService, ShutteringService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter.fromRequest

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.ExecutionContext

@Singleton()
class LiveBankController @Inject() (
  override val authConnector: AuthConnector,
  override val cdConnector: CitizenDetailsConnector,
  @Named("controllers.confidenceLevel") override val confLevel: Int,
  cc: ControllerComponents,
  openBankingService: OpenBankingService,
  shutteringService: ShutteringService
)(implicit val executionContext: ExecutionContext)
    extends BackendController(cc)
    with BankController
    with AccessControl
    with ControllerChecks
    with ErrorHandling
    with JsonHandler {

  override def parser: BodyParser[AnyContent] = controllerComponents.parsers.anyContent
  override val app: String = "Bank-Controller"

  def getBanks(journeyId: JourneyId): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            openBankingService
              .getBanks(journeyId)
              .map { response =>
                Ok(Json.toJson(response))
              }
          }
        }
      }
    }

  override def selectBank(
    sessionDataId: String,
    journeyId: JourneyId
  ): Action[JsValue] =
    validateAcceptWithAuth(acceptHeaderValidationRules).async(parse.json) { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringService.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withErrorWrapper {
            withValidJson[SelectBankRequest] { selectBankRequest =>
              openBankingService
                .selectBank(
                  sessionDataId,
                  selectBankRequest.bankId,
                  journeyId
                )
                .map(_ => Created)
            }
          }
        }
      }
    }
}
