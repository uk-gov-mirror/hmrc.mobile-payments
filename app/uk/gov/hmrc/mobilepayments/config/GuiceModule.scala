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

package uk.gov.hmrc.mobilepayments.config

import com.google.inject.name.Names.named
import com.google.inject.AbstractModule
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.mobilepayments.controllers.api.ApiAccess
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class GuiceModule(
                   environment:   Environment,
                   configuration: Configuration)
  extends AbstractModule {

  val servicesConfig = new ServicesConfig(
    configuration
  )

  override def configure(): Unit = {

    bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector])
    bindConfigInt("controllers.confidenceLevel")
    bindConfigString("openBankingPaymentReturnUrl", "openBankingPaymentReturnUrl")
    bindConfigString("sandboxOpenBankingPaymentUrl", "sandboxOpenBankingPaymentUrl")
    bindConfigString("payByCardReturnUrl", "payByCardReturnUrl")
    bindConfigString("payByCardBackUrl", "payByCardBackUrl")
    bind(classOf[ApiAccess]).toInstance(ApiAccess("PRIVATE"))
    bind(classOf[String])
      .annotatedWith(named("auth"))
      .toInstance(servicesConfig.baseUrl("auth"))
    bind(classOf[String])
      .annotatedWith(named("open-banking"))
      .toInstance(servicesConfig.baseUrl("open-banking"))
    bind(classOf[String])
      .annotatedWith(named("mobile-shuttering"))
      .toInstance(servicesConfig.baseUrl("mobile-shuttering"))
    bind(classOf[String])
      .annotatedWith(named("payments"))
      .toInstance(servicesConfig.baseUrl("payments"))
    bind(classOf[String])
      .annotatedWith(named("citizen-details"))
      .toInstance(servicesConfig.baseUrl("citizen-details"))
    bind(classOf[String])
      .annotatedWith(named("onlinePaymentsBaseUrl"))
      .toInstance(servicesConfig.baseUrl("p800-payments"))
  }

  private def bindConfigInt(path: String): Unit =
    bindConstant()
      .annotatedWith(named(path))
      .to(configuration.underlying.getInt(path))

  private def bindConfigString(
                                name: String,
                                path: String
                              ): Unit =
    bindConstant().annotatedWith(named(name)).to(configuration.underlying.getString(path))
}
