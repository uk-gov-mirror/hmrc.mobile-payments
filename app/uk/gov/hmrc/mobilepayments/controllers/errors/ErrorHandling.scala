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

package uk.gov.hmrc.mobilepayments.controllers.errors

import play.api.mvc.Result
import play.api.{Logger, mvc}
import uk.gov.hmrc.api.controllers.*
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.http.{NotFoundException, *}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import scala.concurrent.{ExecutionContext, Future}

class MalformedRequestException(message: String = "") extends HttpException(message, 400)

case object ErrorUnauthorizedUpstream extends ErrorResponse(401, "UNAUTHORIZED", "Upstream service such as auth returned 401")

case object ErrorMalformedRequest extends ErrorResponse(400, "MALFORMED", "Malformed JSON")

class GrantAccessException(message: String) extends HttpException(message, 401)

class AccountWithLowCL extends GrantAccessException("Unauthorised! Account with low CL!")

class FailToMatchTaxIdOnAuth extends GrantAccessException("Unauthorised! Failure to match  UTR/Reference against logged in user's UTR/reference")

class UtrNotFoundOnAccount extends GrantAccessException("Unauthorised! UTR not found on account!")

class NinoNotFoundOnAccount extends GrantAccessException("Unauthorised! NINO not found on account!")

case object ForbiddenAccess extends ErrorResponse(403, "UNAUTHORIZED", "Access denied!")

case object ErrorUnauthorizedNoUtr extends ErrorResponse(401, "UNAUTHORIZED", "UTR does not exist on account")

trait ErrorHandling {
  self: BackendBaseController =>
  val app: String
  private val logger: Logger = Logger(this.getClass)

  def withErrorWrapper(func: => Future[mvc.Result])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Result] =
    func.recover {
      case ex: UpstreamErrorResponse if ex.statusCode == 401 =>
        logger.warn("Upstream service returned 401")
        Status(ErrorUnauthorizedUpstream.httpStatusCode)

      case ex: UpstreamErrorResponse if ex.statusCode == 404 =>
        logger.warn("Resource not found!")
        Status(ErrorNotFound.httpStatusCode)

      case ex: UpstreamErrorResponse if ex.statusCode == 400 =>
        logger.warn("Malformed JSON")
        Status(ErrorMalformedRequest.httpStatusCode)

      case ex: MissingBearerToken =>
        logger.warn("Upstream service returned 401")
        Status(ErrorUnauthorizedUpstream.httpStatusCode)

      case ex: NotFoundException =>
        logger.warn("Resource not found!")
        Status(ErrorNotFound.httpStatusCode)

      case ex: MalformedRequestException =>
        logger.warn(s"Malformed JSON  ${ex.getMessage}")
        Status(ErrorMalformedRequest.httpStatusCode)(ex.getMessage)

      case ex: FailToMatchTaxIdOnAuth =>
        logger.warn(s"${ex.getMessage}")
        Status(ErrorUnauthorizedUpstream.httpStatusCode)(ex.getMessage)

      case ex: UtrNotFoundOnAccount =>
        logger.warn(s"${ex.getMessage}")
        Status(ErrorUnauthorizedUpstream.httpStatusCode)(ex.getMessage)

      case e: Exception =>
        logger.warn(s"Native Error - $app Internal server error 2: ${e.getMessage}", e)
        Status(ErrorInternalServerError.httpStatusCode)
    }
}
