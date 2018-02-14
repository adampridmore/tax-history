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

package uk.gov.hmrc.taxhistory.services.helper

import org.joda.time.LocalDate
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.rti.RtiData
import uk.gov.hmrc.taxhistory.model.api._
import uk.gov.hmrc.taxhistory.model.nps.EmploymentStatus.Live
import uk.gov.hmrc.taxhistory.model.nps.{EmploymentStatus, Iabd, NpsEmployment, NpsTaxAccount}
import uk.gov.hmrc.taxhistory.model.utils.TestUtil
import uk.gov.hmrc.taxhistory.services.helpers.EmploymentHistoryServiceHelper

class EmploymentHistoryServiceHelperSpec extends PlaySpec with MockitoSugar with TestUtil {

  val npsEmploymentResponseWithTaxDistrictNumber: List[NpsEmployment] = List(
    NpsEmployment(
      "AA000000", 6, "0531", "J4816", "Aldi", Some("6044041000000"), receivingJobSeekersAllowance = false,
      otherIncomeSourceIndicator = false, new LocalDate("2015-01-21"), None, receivingOccupationalPension = false, Live))

  lazy val testRtiData: RtiData = loadFile("/json/rti/response/dummyRti.json").as[RtiData]
  lazy val testIabds: List[Iabd] = loadFile("/json/nps/response/iabds.json").as[List[Iabd]]
  lazy val testNpsTaxAccount: NpsTaxAccount = loadFile("/json/nps/response/GetTaxAccount.json").as[NpsTaxAccount]

  val startDate = new LocalDate("2015-01-21")
  lazy val employment1 = Employment(payeReference = "1234",
    startDate = new LocalDate("2016-10-20"),
    employerName = "AnEmployerName",
    employmentStatus = EmploymentStatus.Live, worksNumber = "00191048716")

  lazy val employment2 = Employment(payeReference = "4321",
    startDate = new LocalDate("2015-12-01"),
    employerName = "AnotherEmployerName",
    employmentStatus = EmploymentStatus.Live, worksNumber = "00191048716")

  lazy val taxAccount = TaxAccount(
    underpaymentAmount = Some(BigDecimal(11.11)),
    outstandingDebtRestriction = Some(BigDecimal(22.22)),
    actualPUPCodedInCYPlusOneTaxYear = Some(BigDecimal(33.33)))

  lazy val companyBenefit = CompanyBenefit(iabdType = "type",
    amount = BigDecimal(123.00))
  lazy val payAndTax = PayAndTax(taxablePayTotal = Some(BigDecimal(2222.22)),
    taxTotal = Some(BigDecimal(111.11)),
    earlierYearUpdates = Nil)
  lazy val payAsYouEarn1 = PayAsYouEarn(
    employments = List(employment1),
    allowances = Nil,
    benefits = Map(employment1.employmentId.toString -> List(companyBenefit)),
    payAndTax = Map(employment1.employmentId.toString -> payAndTax),
    taxAccount = None)

  lazy val payAsYouEarn2 = PayAsYouEarn(
    employments = List(employment2),
    allowances = Nil,
    benefits = Map(employment2.employmentId.toString -> List(companyBenefit)),
    payAndTax = Map(employment2.employmentId.toString -> payAndTax),
    taxAccount = Some(taxAccount))

  "EmploymentHistoryServiceHelper" should {
    "merge from two payAsYouEarn objects into one" in {
      val merged = EmploymentHistoryServiceHelper.combinePAYEs(List(payAsYouEarn1, payAsYouEarn2)).copy(allowances = Nil, taxAccount = Some(taxAccount))
      merged.employments.size mustBe 2
      merged.employments must contain(employment1)
      merged.employments must contain(employment2)

      merged.allowances.size mustBe 0
      merged.allowances mustBe Nil

      merged.benefits.size mustBe 2
      val Some(benefits1) = merged.benefits.get(employment1.employmentId.toString)
      benefits1.size mustBe 1
      benefits1 must contain(companyBenefit)

      val Some(benefits2) = merged.benefits.get(employment2.employmentId.toString)
      benefits2.size mustBe 1
      benefits2 must contain(companyBenefit)

      merged.payAndTax.size mustBe 2
      val Some(payAndTax1) = merged.payAndTax.get(employment1.employmentId.toString)
      payAndTax1 mustBe payAndTax

      val Some(payAndTax2) = merged.payAndTax.get(employment2.employmentId.toString)
      payAndTax2 mustBe payAndTax

      val mergedTaxAccount = merged.taxAccount.get
      mergedTaxAccount.underpaymentAmount mustBe taxAccount.underpaymentAmount
      mergedTaxAccount.actualPUPCodedInCYPlusOneTaxYear mustBe taxAccount.actualPUPCodedInCYPlusOneTaxYear
      mergedTaxAccount.outstandingDebtRestriction mustBe taxAccount.outstandingDebtRestriction
    }

    "merge from one payAsYouEarn objects into one" in {
      val merged = EmploymentHistoryServiceHelper.combinePAYEs(List(payAsYouEarn1)).copy(allowances = Nil, taxAccount = None)
      merged.employments.size mustBe 1
      merged.employments must contain(employment1)

      merged.allowances.size mustBe 0
      merged.allowances mustBe Nil

      merged.benefits.size mustBe 1
      val Some(benefits1) = merged.benefits.get(employment1.employmentId.toString)
      benefits1.size mustBe 1
      benefits1 must contain(companyBenefit)

      merged.payAndTax.size mustBe 1
      val Some(payAndTax1) = merged.payAndTax.get(employment1.employmentId.toString)
      payAndTax1 mustBe payAndTax

      merged.taxAccount mustBe None
    }

    "Build pay as you earn using empty tax account" in {
      val npsEmployments = npsEmploymentResponseWithTaxDistrictNumber
      val payAsYouEarn = EmploymentHistoryServiceHelper.buildPAYE(None, Nil, npsEmployments.head)
      payAsYouEarn.taxAccount mustBe None
    }

    "Build employment1 from rti, nps employment1 and Iabd data" in {
      val npsEmployments = npsEmploymentResponseWithTaxDistrictNumber

      val payAsYouEarn = EmploymentHistoryServiceHelper.buildPAYE(testRtiData.employments.headOption, testIabds, npsEmployments.head)
      val employment = payAsYouEarn.employments.head
      employment.employerName mustBe "Aldi"
      employment.payeReference mustBe "0531/J4816"
      val Some(payAndTax) = payAsYouEarn.payAndTax.get(employment.employmentId.toString)
      payAndTax.taxablePayTotal mustBe Some(BigDecimal.valueOf(20000.00))
      payAndTax.taxTotal mustBe Some(BigDecimal.valueOf(1880.00))
      payAndTax.earlierYearUpdates.size mustBe 1
      payAsYouEarn.employments.head.startDate mustBe startDate
      payAsYouEarn.employments.head.endDate mustBe None
      val Some(companyBenefits) = payAsYouEarn.benefits.get(employment.employmentId.toString)
      companyBenefits.size mustBe 8
    }

    "Build employment1 when there is no  data for rti and Iabd" in {
      val npsEmployments = npsEmploymentResponseWithTaxDistrictNumber

      val payAsYouEarn = EmploymentHistoryServiceHelper.buildPAYE(None, Nil, npsEmployments.head)
      val employment = payAsYouEarn.employments.head
      employment.employerName mustBe "Aldi"
      employment.payeReference mustBe "0531/J4816"
      employment.startDate mustBe startDate
      employment.endDate mustBe None
      val payAndTax = payAsYouEarn.payAndTax.get(employment.employmentId.toString)
      payAndTax mustBe None
      val companyBenefits = payAsYouEarn.benefits.get(employment.employmentId.toString)
      companyBenefits mustBe None

    }
    "Build employment1 when there is data for rti is Nil" in {
      val npsEmployments = npsEmploymentResponseWithTaxDistrictNumber

      val payAsYouEarn = EmploymentHistoryServiceHelper.buildPAYE(None, testIabds, npsEmployments.head)
      val employment = payAsYouEarn.employments.head
      employment.employerName mustBe "Aldi"
      employment.payeReference mustBe "0531/J4816"
      employment.startDate mustBe startDate
      employment.endDate mustBe None
      val payAndTax = payAsYouEarn.payAndTax.get(employment.employmentId.toString)
      payAndTax mustBe None
      val companyBenefits = payAsYouEarn.benefits.get(employment.employmentId.toString)
      companyBenefits.get.size mustBe 8
    }

    "Build employment1 when there is data for Iabd is None or Null" in {
      val npsEmployments = npsEmploymentResponseWithTaxDistrictNumber

      val payAsYouEarn = EmploymentHistoryServiceHelper.buildPAYE(testRtiData.employments.headOption, Nil, npsEmployments.head)
      val employment = payAsYouEarn.employments.head
      employment.employerName mustBe "Aldi"
      employment.payeReference mustBe "0531/J4816"
      employment.startDate mustBe startDate
      employment.endDate mustBe None
      val payAndTax = payAsYouEarn.payAndTax.get(employment.employmentId.toString)
      payAndTax.get.taxablePayTotal mustBe Some(BigDecimal.valueOf(20000.00))
      payAndTax.get.taxTotal mustBe Some(BigDecimal.valueOf(1880.00))
      payAndTax.get.earlierYearUpdates.size mustBe 1
      val companyBenefits = payAsYouEarn.benefits.get(employment.employmentId.toString)
      companyBenefits mustBe None
    }

    "Build employment1 when there is no data for Iabd" in {
      val npsEmployments = npsEmploymentResponseWithTaxDistrictNumber

      val payAsYouEarn = EmploymentHistoryServiceHelper.buildPAYE(testRtiData.employments.headOption, Nil, npsEmployments.head)
      val employment = payAsYouEarn.employments.head
      employment.employerName mustBe "Aldi"
      employment.payeReference mustBe "0531/J4816"
      employment.startDate mustBe startDate
      employment.endDate mustBe None
      val payAndTax = payAsYouEarn.payAndTax.get(employment.employmentId.toString)
      payAndTax.get.taxablePayTotal mustBe Some(BigDecimal.valueOf(20000.00))
      payAndTax.get.taxTotal mustBe Some(BigDecimal.valueOf(1880.00))
      payAndTax.get.earlierYearUpdates.size mustBe 1
      val companyBenefits = payAsYouEarn.benefits.get(employment.employmentId.toString)
      companyBenefits mustBe None
    }
  }
}
