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

package uk.gov.hmrc.mobilepayments.mocks

import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AuthConnector, ConfidenceLevel, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mobilepayments.common.BaseSpec
import uk.gov.hmrc.mobilepayments.connectors.CitizenDetailsConnector

import scala.concurrent.{ExecutionContext, Future}

trait AuthorisationStub extends BaseSpec {

  type GrantAccess = ConfidenceLevel ~ Enrolments
  type GrantAccess1 = Option[String] ~ Enrolments
  type GrantAccess2 = Option[String] ~ Option[String] ~ Enrolments

  def addEnrolments(name: String, identifier: String, key: String, enrolmentState: String = "Activated") =
    Enrolment(name, identifiers = Seq(EnrolmentIdentifier(identifier, key)), state = enrolmentState)

  val saOnlyEnrolments: Set[Enrolment] =
    Set(addEnrolments("IR-SA", "UTR", "12212321"))

  val mtdOnlyEnrolments: Set[Enrolment] =
    Set(addEnrolments("HMRC-MTD-ID", "MTDITID", "1234567890"))
  val saMTDEnrolments: Set[Enrolment] =
    Set(addEnrolments("IR-SA", "UTR", "12212321"), addEnrolments("HMRC-MTD-ID", "MTDITID", "1234567890"))
  val deactivatedSAEnrolment: Set[Enrolment] =
    Set(addEnrolments("IR-SA", "UTR", "12212321", "deactivated"))
  val deactivatedMTDEnrolment: Set[Enrolment] =
    Set(addEnrolments("HMRC-MTD-ID", "MTDITID", "1234567890", "deactivated"))

  val confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200
  val authorisedResponse: GrantAccess = new ~(confidenceLevel, Enrolments(saOnlyEnrolments))
  val utrAndEnrolmentResponse: GrantAccess1 = new ~(Some("12212321"), Enrolments(saOnlyEnrolments))
  def getNinoUtrEnrolmentResponse(nino: Option[String], utr: Option[String], enrolments: Set[Enrolment] = Set.empty) = new ~(
    new ~(
      nino,
      utr
    ),
    Enrolments(enrolments)
  )
  val authorisedLowCLResponse: GrantAccess = new ~(ConfidenceLevel.L50, Enrolments(saOnlyEnrolments))

  def stubAuthorisationGrantAccess(response: GrantAccess)(implicit authConnector: AuthConnector) =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[GrantAccess])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future successful response)

  def stubAuthorisationWithAuthorisationException()(implicit authConnector: AuthConnector) =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[GrantAccess])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future failed UpstreamErrorResponse("Error", 401, 401))

  def stubGetNinoFromAuth(response: Option[String])(implicit authConnector: AuthConnector) =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[Option[String]])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future successful response)

  def stubGetNinoAndUTRFromAuth(response: GrantAccess2)(implicit authConnector: AuthConnector) =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[GrantAccess2])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future successful response)

  def stubGetUTRByNino(response: Future[Option[SaUtr]])(implicit cdConnector: CitizenDetailsConnector) =
    (cdConnector
      .getUtrByNino(_: String)(_: HeaderCarrier))
      .expects(*, *)
      .returning(response)

  def stubGetUTRFromAuth(response: GrantAccess1)(implicit authConnector: AuthConnector) =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[GrantAccess1])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future successful response)
}
