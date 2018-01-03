/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.taxhistory.services

import org.mockito.Matchers
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.taxhistory.connectors.des.RtiConnector
import uk.gov.hmrc.taxhistory.connectors.nps.NpsConnector
import uk.gov.hmrc.taxhistory.model.api.{IndividualTaxYear, PayAsYouEarn}
import uk.gov.hmrc.taxhistory.model.utils.TestUtil
import uk.gov.hmrc.time.TaxYear

import scala.concurrent.Future


class TaxYearServiceSpec extends PlaySpec with MockitoSugar with TestUtil{
  private val mockAudit= mock[Audit]

  implicit val hc = HeaderCarrier()
  val testNino = randomNino().toString()
  object TestEmploymentService extends EmploymentHistoryService {
    override def audit: Audit = mockAudit
  }

  "get Individual Tax Years " should {
    "successfully return list of four tax years" in {
      val response =  await(TestEmploymentService.getTaxYears(testNino))
      response mustBe a[HttpResponse]
      response.status mustBe OK
      val taxYears = response.json.as[List[IndividualTaxYear]]
      taxYears.size mustBe 4

      val cyMinus1 = TaxYear.current.previous
      taxYears.head.year mustBe cyMinus1.startYear
      taxYears.head.allowancesURI mustBe s"/${cyMinus1.startYear}/allowances"
      taxYears.head.employmentsURI mustBe s"/${cyMinus1.startYear}/employments"

      val cyMinus2 = cyMinus1.previous
      taxYears(1).year mustBe cyMinus2.startYear
      taxYears(1).allowancesURI mustBe s"/${cyMinus2.startYear}/allowances"
      taxYears(1).employmentsURI mustBe s"/${cyMinus2.startYear}/employments"

      val cyMinus3 = cyMinus2.previous
      taxYears(2).year mustBe cyMinus3.startYear
      taxYears(2).allowancesURI mustBe s"/${cyMinus3.startYear}/allowances"
      taxYears(2).employmentsURI mustBe s"/${cyMinus3.startYear}/employments"

      val cyMinus4 = cyMinus3.previous
      taxYears(3).year mustBe cyMinus4.startYear
      taxYears(3).allowancesURI mustBe s"/${cyMinus4.startYear}/allowances"
      taxYears(3).employmentsURI mustBe s"/${cyMinus4.startYear}/employments"

    }
  }
}
