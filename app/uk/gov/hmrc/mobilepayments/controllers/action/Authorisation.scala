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

package uk.gov.hmrc.mobilepayments.controllers.action

import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.*
import uk.gov.hmrc.api.controllers.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.*
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthorisedFunctions, ConfidenceLevel, CredentialStrength}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mobilepayments.controllers.errors.{AccountWithLowCL, ErrorUnauthorizedNoUtr, FailToMatchTaxIdOnAuth, ForbiddenAccess, UtrNotFoundOnAccount}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

trait Authorisation extends Results with AuthorisedFunctions {

  val confLevel: Int
  private val logger: Logger = Logger(this.getClass)

  lazy val requiresAuth = true
  private lazy val lowConfidenceLevel = new AccountWithLowCL

  def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    authorised(CredentialStrength("strong") and ConfidenceLevel.L200)
      .retrieve(confidenceLevel and allEnrolments) { case foundConfidenceLevel ~ enrolments =>
        if (confLevel > foundConfidenceLevel.level) throw lowConfidenceLevel
        else Future successful true
      }

  def invokeAuthBlock[A](
    request: Request[A],
    block: Request[A] => Future[Result]
  )(implicit ec: ExecutionContext): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    grantAccess()
      .flatMap { _ =>
        block(request)
      }
      .recover {
        case e: UpstreamErrorResponse if e.statusCode > 399 && e.statusCode < 500 =>
          logger.info("Unauthorized! Failed to grant access since 4xx response!")
          Unauthorized(Json.toJson(ErrorUnauthorized.asInstanceOf[ErrorResponse]))

        case _: UtrNotFoundOnAccount =>
          logger.info("Unauthorized! UTR not found on account!")
          Unauthorized(Json.toJson[ErrorResponse](ErrorUnauthorizedNoUtr))

        case _: FailToMatchTaxIdOnAuth =>
          logger.info("Forbidden! Failure to match URL UTR against Auth UTR")
          Forbidden(Json.toJson[ErrorResponse](ForbiddenAccess))

        case _: AccountWithLowCL =>
          logger.info("Unauthorized! Account with low CL!")
          Unauthorized(Json.toJson(ErrorUnauthorizedLowCL.asInstanceOf[ErrorResponse]))
      }
  }

}

trait AccessControl extends HeaderValidator with Authorisation {
  outer =>
  def parser: BodyParser[AnyContent]

  def validateAcceptWithAuth(
    rules: Option[String] => Boolean
  )(implicit ec: ExecutionContext): ActionBuilder[Request, AnyContent] =
    new ActionBuilder[Request, AnyContent] {

      override def parser: BodyParser[AnyContent] = outer.parser
      override protected def executionContext: ExecutionContext = outer.executionContext

      def invokeBlock[A](
        request: Request[A],
        block: Request[A] => Future[Result]
      ): Future[Result] =
        if (rules(request.headers.get("Accept"))) {
          if (requiresAuth) invokeAuthBlock(request, block)
          else block(request)
        } else
          Future.successful(
            Status(ErrorAcceptHeaderInvalid.httpStatusCode)(
              Json.toJson(ErrorAcceptHeaderInvalid.asInstanceOf[ErrorResponse])
            )
          )
    }

  def getNinoFromAuth(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[String]] =
    authorised().retrieve(nino)(foundNino => Future successful foundNino)

  def getSaUTRFromAuth(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[SaUtr]] =
    authorised()
      .retrieve(saUtr and allEnrolments) { case sautrOpt ~ enrolments =>
        val enrolmentSautr: Option[String] = enrolments.enrolments
          .find(enKey => enKey.key == "IR-SA")
          .flatMap { enrolment =>
            enrolment.identifiers
              .find(id => id.key.toUpperCase == "UTR" && enrolment.state == "Activated")
              .map(key => key.value)
          }

        (sautrOpt, enrolmentSautr) match {
          case (Some(utr1), Some(enrolmentUtr)) => if (enrolmentUtr == utr1) Future.successful(enrolmentSautr.map(SaUtr(_))) else Future.successful(None)
          case (None, enrolmentUtrOpt)          => Future.successful(enrolmentSautr.map(SaUtr(_)))
          case (sautrOpt, None)                 => Future.successful(sautrOpt.map(SaUtr(_)))
          case (None, None)                     => Future.successful(None)

        }
      }

}
