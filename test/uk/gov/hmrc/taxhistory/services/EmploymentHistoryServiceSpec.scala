/*
 * Copyright 2019 HM Revenue & Customs
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

import java.util.UUID

import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.tai.model.rti.RtiData
import uk.gov.hmrc.taxhistory.fixtures.Employments
import uk.gov.hmrc.taxhistory.model.api.EmploymentPaymentType.OccupationalPension
import uk.gov.hmrc.taxhistory.model.api.{CompanyBenefit, Employment, PayAsYouEarn}
import uk.gov.hmrc.taxhistory.model.nps.EmploymentStatus.Live
import uk.gov.hmrc.taxhistory.model.nps.{EmploymentStatus, Iabd, NpsEmployment, NpsTaxAccount}
import uk.gov.hmrc.taxhistory.model.utils.{PlaceHolder, TestUtil}
import uk.gov.hmrc.taxhistory.utils.TestEmploymentHistoryService
import uk.gov.hmrc.time.TaxYear

import scala.concurrent.Future


class EmploymentHistoryServiceSpec extends UnitSpec with MockitoSugar with TestUtil with Employments {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testNino = randomNino()

  val testEmploymentHistoryService: EmploymentHistoryService = TestEmploymentHistoryService.createNew()

  val npsEmploymentResponse: List[NpsEmployment] = List(
    NpsEmployment(
      "AA000000", 1, "531", "J4816", "Aldi", Some("6044041000000"), receivingJobSeekersAllowance = false,
      otherIncomeSourceIndicator = false, Some(new LocalDate("2015-01-21")), None, receivingOccupationalPension = true, Live))

  val npsEmploymentWithJobSeekerAllowanceCY: List[NpsEmployment] = List(
    NpsEmployment(
      "AA000000", 1, "531", "J4816", "Aldi", Some("6044041000000"), receivingJobSeekersAllowance = true, otherIncomeSourceIndicator = false,
      Some(new LocalDate(s"${TaxYear.current.currentYear}-01-21")), None, receivingOccupationalPension = false, Live))

  val npsEmploymentWithJobSeekerAllowanceCYMinus1: List[NpsEmployment] = List(
    NpsEmployment(
      "AA000000", 1, "531", "J4816", "Aldi", Some("6044041000000"), receivingJobSeekersAllowance = true, otherIncomeSourceIndicator = false,
      Some(new LocalDate(s"${TaxYear.current.previous.currentYear}-01-21")), None, receivingOccupationalPension = false, Live))

  val npsEmploymentWithOtherIncomeSourceIndicator: List[NpsEmployment] = List(
    NpsEmployment(
      "AA000000", 1, "531", "J4816", "Aldi", Some("6044041000000"), receivingJobSeekersAllowance = false, otherIncomeSourceIndicator = true,
      Some(new LocalDate("2015-01-21")), None, receivingOccupationalPension = false, Live))


  val npsEmploymentWithJustJobSeekerAllowance: List[NpsEmployment] = List(
    NpsEmployment(
      "AA000000", 1, "531", "J4816", "Aldi", Some("6044041000000"), receivingJobSeekersAllowance = true, otherIncomeSourceIndicator = false,
      Some(new LocalDate("2015-01-21")), None, receivingOccupationalPension = false, Live))

  val npsEmploymentWithJustOtherIncomeSourceIndicator: List[NpsEmployment] = List(
    NpsEmployment(
      "AA000000", 1, "531", "J4816", "Aldi", Some("6044041000000"), receivingJobSeekersAllowance = false, otherIncomeSourceIndicator = true,
      Some(new LocalDate("2015-01-21")), None, receivingOccupationalPension = false, Live))


  lazy val testRtiData: RtiData = loadFile("/json/rti/response/dummyRti.json").as[RtiData]
  lazy val rtiDuplicateEmploymentsResponse: JsValue = loadFile("/json/rti/response/dummyRtiDuplicateEmployments.json")
  lazy val rtiPartialDuplicateEmploymentsResponse: JsValue = loadFile("/json/rti/response/dummyRtiPartialDuplicateEmployments.json")
  lazy val rtiNonMatchingEmploymentsResponse: JsValue = loadFile("/json/rti/response/dummyRtiNonMatchingEmployment.json")
  lazy val testNpsTaxAccount: NpsTaxAccount = loadFile("/json/nps/response/GetTaxAccount.json").as[NpsTaxAccount]
  lazy val testIabds: List[Iabd] = loadFile("/json/nps/response/iabds.json").as[List[Iabd]]

  val startDate = new LocalDate("2015-01-21")

  private def stubNpsGetEmploymentsSucceeds(npsEmployments: List[NpsEmployment]) = {
    when(testEmploymentHistoryService.desNpsConnector.getEmployments(any(), any()))
      .thenReturn(Future.successful(npsEmployments))
  }

  private def stubRtiGetEmploymentsSucceeds(rtiEmployments: Option[RtiData]) = {
    when(testEmploymentHistoryService.rtiConnector.getRTIEmployments(any(), any()))
      .thenReturn(Future.successful(rtiEmployments))
  }

  private def stubNpsGetTaxAccountSucceeds(optTaxAccount: Option[NpsTaxAccount]) = {
    when(testEmploymentHistoryService.desNpsConnector.getTaxAccount(any(), any()))
      .thenReturn(Future.successful(optTaxAccount))
  }

  private def stubNpsGetIabdsSucceeds(iabds: List[Iabd]) = {
    when(testEmploymentHistoryService.desNpsConnector.getIabds(any(), any()))
      .thenReturn(Future.successful(iabds))
  }

  private def stubNpsGetEmploymentsFails(failure: Throwable) = {
    when(testEmploymentHistoryService.desNpsConnector.getEmployments(any(), any()))
      .thenReturn(Future.failed(failure))
  }

  private def stubNpsGetTaxAccountFails(failure: Throwable) = {
    when(testEmploymentHistoryService.desNpsConnector.getTaxAccount(any(), any()))
      .thenReturn(Future.failed(failure))
  }

  private def stubNpsGetIabdFails(failure: Throwable) = {
    when(testEmploymentHistoryService.desNpsConnector.getIabds(any(), any()))
      .thenReturn(Future.failed(failure))
  }

  private def stubRtiGetEmploymentsFails(failure: Throwable) = {
    when(testEmploymentHistoryService.rtiConnector.getRTIEmployments(any(), any()))
      .thenReturn(Future.failed(failure))
  }

  class StubConnectors(npsGetEmployments: => OngoingStubbing[Future[List[NpsEmployment]]] = stubNpsGetEmploymentsSucceeds(npsEmploymentResponse),
                       npsGetTaxAccount: => OngoingStubbing[Future[Option[NpsTaxAccount]]] = stubNpsGetTaxAccountSucceeds(Some(testNpsTaxAccount)),
                       npsGetIabdDetails: => OngoingStubbing[Future[List[Iabd]]] = stubNpsGetIabdsSucceeds(testIabds),
                       rti: => OngoingStubbing[Future[Option[RtiData]]] = stubRtiGetEmploymentsSucceeds(Some(testRtiData))) {
    npsGetEmployments
    npsGetTaxAccount
    npsGetIabdDetails
    rti
  }

  "Employment Service" should {
    "successfully get Nps Employments Data" in
      new StubConnectors(npsGetEmployments = stubNpsGetEmploymentsSucceeds(npsEmploymentResponse)) {
        noException shouldBe thrownBy(await(testEmploymentHistoryService.retrieveNpsEmployments(testNino, TaxYear(2016))))

    }

    "successfully get Nps Employments Data with jobseekers allowance for cy-1" in
      new StubConnectors(npsGetEmployments = stubNpsGetEmploymentsSucceeds(npsEmploymentWithJobSeekerAllowanceCYMinus1)) {
      noException shouldBe thrownBy(await(testEmploymentHistoryService.retrieveNpsEmployments(testNino, TaxYear(2016))))
    }

    "return any non success status response from get Nps Employments api" in
      new StubConnectors(npsGetEmployments = stubNpsGetEmploymentsFails(new BadRequestException(""))) {
      intercept[BadRequestException](await(testEmploymentHistoryService.retrieveNpsEmployments(testNino, TaxYear(2016))))
    }

    "successfully get Rti Employments Data" in
      new StubConnectors(rti = stubRtiGetEmploymentsSucceeds(Some(testRtiData))) {
      await(testEmploymentHistoryService.retrieveRtiData(testNino, TaxYear(2016))) shouldBe Some(testRtiData)
    }

    "successfully get no RTI employments data if RTI connector returns None" in
    new StubConnectors(rti = stubRtiGetEmploymentsSucceeds(None)) {
      await(testEmploymentHistoryService.retrieveRtiData(testNino, TaxYear(2016))) shouldBe None
    }

    "fail with NotFoundException if the NPS Get Employments API was successful but returned zero employments" in
      new StubConnectors(npsGetEmployments = stubNpsGetEmploymentsSucceeds(List.empty)) {
      intercept[NotFoundException](await(testEmploymentHistoryService.retrieveAndBuildPaye(testNino, TaxYear(2016))))
    }

    "fail with NotFoundException if the NPS Get Employments API failed with a NotFoundException" in
      new StubConnectors(npsGetEmployments = stubNpsGetEmploymentsFails(new NotFoundException("NPS API returned 404"))) {
        intercept[NotFoundException](await(testEmploymentHistoryService.retrieveAndBuildPaye(testNino, TaxYear(2016))))
      }

    "throw an exception when the call to get RTI employments fails" in
      new StubConnectors(rti = stubRtiGetEmploymentsFails(new BadRequestException(""))) {
      intercept[BadRequestException](await(testEmploymentHistoryService.retrieveAndBuildPaye(testNino, TaxYear(2016))))
    }

    "succeeds when the RTI call fails with a 404 (i.e the RTI connector returns None)" in
      new StubConnectors(rti = stubRtiGetEmploymentsSucceeds(None)) {
        noException shouldBe thrownBy(await(testEmploymentHistoryService.retrieveAndBuildPaye(testNino, TaxYear(2016))))
      }

    "succeeds when the get IABD call fails with a 404 (i.e the RTI connector returns None)" in
      new StubConnectors(npsGetIabdDetails = stubNpsGetIabdsSucceeds(List())) {
        noException shouldBe thrownBy(await(testEmploymentHistoryService.retrieveAndBuildPaye(testNino, TaxYear(2016))))
      }

    "throw an exception when the call to get NPS tax account fails" in
      new StubConnectors(npsGetTaxAccount = stubNpsGetTaxAccountFails(new BadRequestException(""))) {
      intercept[BadRequestException](await(testEmploymentHistoryService.retrieveAndBuildPaye(testNino, TaxYear(2016))))
    }

    "return success response from get Employments" in {
      stubNpsGetEmploymentsSucceeds(npsEmploymentResponse)
      stubNpsGetIabdsSucceeds(testIabds)
      stubRtiGetEmploymentsSucceeds(Some(testRtiData))
      stubNpsGetTaxAccountSucceeds(Some(testNpsTaxAccount))

      val paye = await(testEmploymentHistoryService.retrieveAndBuildPaye(testNino, TaxYear(2016)))

      val employments = paye.employments
      employments.size shouldBe 1
      employments.head.employerName shouldBe "Aldi"
      employments.head.payeReference shouldBe "531/J4816"
      employments.head.startDate shouldBe Some(startDate)
      employments.head.endDate shouldBe None
      employments.head.employmentPaymentType shouldBe Some(OccupationalPension)

      val Some(payAndTax) = paye.payAndTax.get(employments.head.employmentId.toString)
      payAndTax.taxablePayTotal shouldBe Some(BigDecimal.valueOf(20000.00))
      payAndTax.taxablePayTotalIncludingEYU shouldBe Some(BigDecimal.valueOf(19399.01))
      payAndTax.taxTotal shouldBe Some(BigDecimal.valueOf(1880.00))
      payAndTax.taxTotalIncludingEYU shouldBe Some(BigDecimal.valueOf(1869.01))
      payAndTax.earlierYearUpdates.size shouldBe 1

      val eyu = payAndTax.earlierYearUpdates.head
      eyu.receivedDate shouldBe new LocalDate("2016-06-01")
      eyu.taxablePayEYU shouldBe BigDecimal(-600.99)
      eyu.taxEYU shouldBe BigDecimal(-10.99)

      val Some(statePension) = paye.statePension
      statePension.grossAmount shouldBe BigDecimal(1253.23)
      statePension.typeDescription shouldBe "State Pension"

      val Some(benefits) = paye.benefits.get(employments.head.employmentId.toString)
      benefits.size shouldBe 2
      benefits.head.iabdType shouldBe "CarFuelBenefit"
      benefits.head.amount shouldBe BigDecimal(100)
      benefits.last.iabdType shouldBe "VanBenefit"
      benefits.last.amount shouldBe BigDecimal(100)
    }

    "successfully merge rti and nps employment1 data into employment1 list" in {
      val payAsYouEarn =
        testEmploymentHistoryService.mergeEmployments(
          nino = testNino,
          taxYear = TaxYear.current.previous,
          npsEmployments = npsEmploymentResponse,
          rtiEmployments = testRtiData.employments,
          taxAccountOption = Some(testNpsTaxAccount),
          iabds = testIabds
        )

      val employment = payAsYouEarn.employments.head
      val Some(payAndTax) = payAsYouEarn.payAndTax.get(employment.employmentId.toString)
      val Some(benefits) = payAsYouEarn.benefits.get(employment.employmentId.toString)
      val Some(statePension) = payAsYouEarn.statePension

      employment.employerName shouldBe "Aldi"
      employment.payeReference shouldBe "531/J4816"
      employment.startDate shouldBe Some(startDate)
      employment.endDate shouldBe None
      employment.isOccupationalPension shouldBe true
      employment.employmentPaymentType shouldBe Some(OccupationalPension)
      payAndTax.taxablePayTotal shouldBe Some(BigDecimal.valueOf(20000.00))
      payAndTax.taxablePayTotalIncludingEYU shouldBe Some(BigDecimal.valueOf(19399.01))
      payAndTax.taxTotal shouldBe Some(BigDecimal.valueOf(1880.00))
      payAndTax.taxTotalIncludingEYU shouldBe Some(BigDecimal.valueOf(1869.01))
      payAndTax.earlierYearUpdates.size shouldBe 1
      val eyu = payAndTax.earlierYearUpdates.head
      eyu.taxablePayEYU shouldBe BigDecimal(-600.99)
      eyu.taxEYU shouldBe BigDecimal(-10.99)
      eyu.receivedDate shouldBe new LocalDate("2016-06-01")
      benefits.size shouldBe 2
      benefits.head.iabdType shouldBe "CarFuelBenefit"
      benefits.head.amount shouldBe BigDecimal(100)
      benefits.last.iabdType shouldBe "VanBenefit"
      benefits.last.amount shouldBe BigDecimal(100)
      statePension.grossAmount shouldBe BigDecimal(1253.23)
      statePension.typeDescription shouldBe "State Pension"
    }

    "successfully exclude nps employment1 data" when {

      "nps receivingJobseekersAllowance is true for CY" in
        new StubConnectors(npsGetEmployments = stubNpsGetEmploymentsSucceeds(npsEmploymentWithJobSeekerAllowanceCY)) {
        intercept[NotFoundException](await(testEmploymentHistoryService.retrieveAndBuildPaye(testNino, TaxYear(TaxYear.current.currentYear))))
      }

      "otherIncomeSourceIndicator is true from list of employments" in
        new StubConnectors(npsGetEmployments = stubNpsGetEmploymentsSucceeds(npsEmploymentWithOtherIncomeSourceIndicator)) {
        intercept[NotFoundException](await(testEmploymentHistoryService.retrieveAndBuildPaye(testNino, TaxYear(2016))))
      }
    }

    "throw not found error" when {

      "nps employments contain single element with npsEmploymentWithJustOtherIncomeSourceIndicator attribute is true" in
        new StubConnectors(npsGetEmployments = stubNpsGetEmploymentsSucceeds(npsEmploymentWithJustOtherIncomeSourceIndicator)) {
        intercept[NotFoundException](await(testEmploymentHistoryService.retrieveAndBuildPaye(testNino, TaxYear(2016))))
      }
    }

    "propagate exception if NPS IABD API connector fails with an exception" in
      new StubConnectors(npsGetIabdDetails = stubNpsGetIabdFails(new BadRequestException(""))) {
      intercept[BadRequestException](await(testEmploymentHistoryService.retrieveNpsIabds(testNino, TaxYear(2016))))
    }

    "propagate exception if NPS Get Tax Account API connector fails with an exception" in
      new StubConnectors(npsGetTaxAccount = stubNpsGetTaxAccountFails(new BadRequestException(""))) {
      intercept[BadRequestException](await(testEmploymentHistoryService.getTaxAccount(testNino, TaxYear.current.previous)))
    }

    "fail with a NotFoundException if NPS Get Tax Account API connector returns None (i.e. the API returned a 404)" when {
      "asking for current year" in
        new StubConnectors(npsGetTaxAccount = stubNpsGetTaxAccountSucceeds(None)) {
          intercept[NotFoundException](await(testEmploymentHistoryService.getTaxAccount(testNino, TaxYear.current)))
        }

      "asking for previous year" in
        new StubConnectors(npsGetTaxAccount = stubNpsGetTaxAccountSucceeds(None)) {
          intercept[NotFoundException](await(testEmploymentHistoryService.getTaxAccount(testNino, TaxYear.current.previous)))
        }
    }

    "fail with a NotFoundException  from get Nps Tax Account api for not found response " in {
      when(testEmploymentHistoryService.desNpsConnector.getTaxAccount(any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[NotFoundException](await(testEmploymentHistoryService.retrieveNpsTaxAccount(testNino, TaxYear(2016))))
    }

    "fetch Employments successfully from cache" in {
      val taxYear = TaxYear.current.previous

      val placeHolders = Seq(PlaceHolder("%taxYearStartYear%", taxYear.startYear.toString), PlaceHolder("%taxYearFinishYear%", taxYear.finishYear.toString))
      lazy val paye = loadFile("/json/withPlaceholders/model/api/paye.json", placeHolders).as[PayAsYouEarn]

      val testEmployment2 =
        Employment(UUID.fromString("01318d7c-bcd9-47e2-8c38-551e7ccdfae3"),
          Some(locaDateCyMinus1("01", "21")), Some(locaDateCyMinus1("02", "21")), "paye-1", "employer-1",
          Some(s"/${taxYear.startYear}/employments/01318d7c-bcd9-47e2-8c38-551e7ccdfae3/company-benefits"),
          Some(s"/${taxYear.startYear}/employments/01318d7c-bcd9-47e2-8c38-551e7ccdfae3/pay-and-tax"),
          Some(s"/${taxYear.startYear}/employments/01318d7c-bcd9-47e2-8c38-551e7ccdfae3"),
          None, Live, "00191048716")

      val testEmployment3 = Employment(UUID.fromString("019f5fee-d5e4-4f3e-9569-139b8ad81a87"),
        Some(locaDateCyMinus1("02", "22")), None, "paye-2", "employer-2",
        Some(s"/${taxYear.startYear}/employments/019f5fee-d5e4-4f3e-9569-139b8ad81a87/company-benefits"),
        Some(s"/${taxYear.startYear}/employments/019f5fee-d5e4-4f3e-9569-139b8ad81a87/pay-and-tax"),
        Some(s"/${taxYear.startYear}/employments/019f5fee-d5e4-4f3e-9569-139b8ad81a87"),
        None, Live, "00191048716")

      // Set up the test data in the cache
      await(testEmploymentHistoryService.cacheService.insertOrUpdate((Nino("AA000000A"), taxYear), paye))

      val employments = await(testEmploymentHistoryService.getEmployments(Nino("AA000000A"), taxYear))
      employments.head.employmentStatus shouldBe EmploymentStatus.Unknown

      employments.head.startDate shouldBe Some(taxYear.starts.withMonthOfYear(4).withDayOfMonth(6))
      employments.head.endDate shouldBe Some(taxYear.finishes.withMonthOfYear(1).withDayOfMonth(20))
      employments should contain(testEmployment2)
      employments should contain(testEmployment3)
    }

    "return an empty list when no employment(not including pensions) was returned from cache" in {
      val paye = PayAsYouEarn(employments = Nil)

      await(testEmploymentHistoryService.cacheService.insertOrUpdate((Nino("AA000000A"), TaxYear(2014)), paye))

      val employments = await(testEmploymentHistoryService.getEmployments(Nino("AA000000A"), TaxYear(2014)))
      employments shouldBe List.empty
    }


    "get Employment successfully" in {
      val paye = loadFile("/json/model/api/paye.json").as[PayAsYouEarn]

      val testEmployment = Employment(UUID.fromString("01318d7c-bcd9-47e2-8c38-551e7ccdfae3"),
        Some(new LocalDate("2016-01-21")), Some(new LocalDate("2017-01-01")), "paye-1", "employer-1",
        Some("/2014/employments/01318d7c-bcd9-47e2-8c38-551e7ccdfae3/company-benefits"),
        Some("/2014/employments/01318d7c-bcd9-47e2-8c38-551e7ccdfae3/pay-and-tax"),
        Some("/2014/employments/01318d7c-bcd9-47e2-8c38-551e7ccdfae3"),
        None, Live, "00191048716")

      await(testEmploymentHistoryService.cacheService.insertOrUpdate((Nino("AA000000A"), TaxYear(2014)), paye))

      val employment = await(testEmploymentHistoryService.getEmployment(Nino("AA000000A"), TaxYear(2014), "01318d7c-bcd9-47e2-8c38-551e7ccdfae3"))
      employment shouldBe testEmployment
    }


    "get Employment return none" in {
      lazy val paye = loadFile("/json/model/api/paye.json").as[PayAsYouEarn]

      await(testEmploymentHistoryService.cacheService.insertOrUpdate((Nino("AA000000A"), TaxYear(2014)), paye))

      intercept[NotFoundException](await(testEmploymentHistoryService.getEmployment(
        Nino("AA000000A"), TaxYear(2014), "01318d7c-bcd9-47e2-8c38-551e7ccdfae6")))
    }

    "get company benefits from cache successfully" in {
      val paye = loadFile("/json/model/api/paye.json").as[PayAsYouEarn]

      val testCompanyBenefits: List[CompanyBenefit] = List(CompanyBenefit(
        UUID.fromString("c9923a63-4208-4e03-926d-7c7c88adc7ee"), "companyBenefitType", 12))

      await(testEmploymentHistoryService.cacheService.insertOrUpdate((Nino("AA000000A"), TaxYear(2014)), paye))

      val companyBenefits = await(testEmploymentHistoryService.getCompanyBenefits(
        Nino("AA000000A"), TaxYear(2014), "01318d7c-bcd9-47e2-8c38-551e7ccdfae3"))

      companyBenefits shouldBe testCompanyBenefits
    }

    "return not found when no company benefits returned from cache" in {
      lazy val payeNoBenefits = loadFile("/json/model/api/payeNoCompanyBenefits.json").as[PayAsYouEarn]

      await(testEmploymentHistoryService.cacheService.insertOrUpdate((Nino("AA000000A"), TaxYear(2014)), payeNoBenefits))

      intercept[NotFoundException](await(testEmploymentHistoryService.getCompanyBenefits(
        Nino("AA000000A"), TaxYear(2014), "01318d7c-bcd9-47e2-8c38-551e7ccdfae3")))
    }

    "return no company benefits from cache for current year" in {
      val taxAccount = await(testEmploymentHistoryService.getCompanyBenefits(
        Nino("AA000000A"), TaxYear.current, "01318d7c-bcd9-47e2-8c38-551e7ccdfae3"))
      taxAccount shouldBe List.empty
    }
  }

  "withEmploymentGaps" should {

    def isNoRecordEmployment(employment: Employment): Boolean =
      employment.employerName == "No record held" && employment.employmentStatus == EmploymentStatus.Unknown

    "return the original list when no employment gaps exist" in {
      val employments = List(liveOngoingEmployment)
      testEmploymentHistoryService.addFillers(employments, TaxYear.current) shouldBe employments
    }

    "return a list with one entry when no employments exist" in {
      val employments = List.empty[Employment]
      testEmploymentHistoryService.addFillers(employments, TaxYear.current) map isNoRecordEmployment shouldBe Seq(true)
    }

    "return a list with no gaps, when original employment has a gap at the start" in {
      val employments = List(liveMidYearEmployment, liveEndYearEmployment)
      testEmploymentHistoryService.addFillers(employments, TaxYear.current) map isNoRecordEmployment shouldBe Seq(true, false, false)
    }

    "return a list with no gaps, when employments overlap and have gaps at the start" in {
      val employments = List(liveNoEndEmployment, liveMidYearEmployment)
      testEmploymentHistoryService.addFillers(employments, TaxYear.current) map isNoRecordEmployment shouldBe Seq(true, false, false)    }

    "return a list with no gaps, when ceased employments overlap and have gaps" in {
      val employments = List(ceasedBeforeStartEmployment, ceasedNoEndEmployment, ceasedAfterEndEmployment)
      testEmploymentHistoryService.addFillers(employments, TaxYear.current) map isNoRecordEmployment shouldBe Seq(false, true, false, false)
    }

    "return a list with no gaps, when potentially ceased employments overlap and have gaps" in {
      val employments = List(ceasedBeforeStartEmployment, liveMidYearEmployment, potentiallyCeasedEmployment)
      testEmploymentHistoryService.addFillers(employments, TaxYear.current) map isNoRecordEmployment shouldBe Seq(false, true, false, false)
    }

    "return a list with no gaps, when original employment has a gap in the middle" in {
      val employments = List(liveStartYearEmployment, liveEndYearEmployment)
      testEmploymentHistoryService.addFillers(employments, TaxYear.current) map isNoRecordEmployment shouldBe Seq(false, true, false)
    }

    "return a list with no gaps, when original employment has a gap at the end" in {
      val employments = List(liveStartYearEmployment, liveMidYearEmployment)
      testEmploymentHistoryService.addFillers(employments, TaxYear.current) map isNoRecordEmployment shouldBe Seq(false, true, false, true)
    }

    "return a list with no gaps, when original employment has a gap at the start and end" in {
      val employments = List(liveMidYearEmployment)
      testEmploymentHistoryService.addFillers(employments, TaxYear.current) map isNoRecordEmployment shouldBe Seq(true, false, true)
    }

    def oneDayBefore(thisDay: Option[LocalDate]) = thisDay.map(_.minusDays(1))
    def oneDayAfter(thisDay: Option[LocalDate]) = thisDay.map(_.plusDays(1))

    "when original employment has no start date but has an end date, and there are no employments afterwards" in {
      val employmentWithoutStartDate = liveMidYearEmployment.copy(startDate = None)
      val filledEmployments = testEmploymentHistoryService.addFillers(List(employmentWithoutStartDate), TaxYear.current)

      val isOrderedFirstInList = filledEmployments.head == employmentWithoutStartDate
      isOrderedFirstInList shouldBe true

      filledEmployments map isNoRecordEmployment shouldBe Seq(false, true, true)

      val firstGap = filledEmployments(1)
      val secondGap = filledEmployments(2)
      firstGap.startDate shouldBe Some(TaxYear.current.starts)
      firstGap.endDate shouldBe oneDayBefore(employmentWithoutStartDate.endDate)

      secondGap.startDate shouldBe oneDayAfter(employmentWithoutStartDate.endDate)
      secondGap.endDate shouldBe None
    }

    "when original employment has no start date but has an end date, and the employment finishes at the end of the tax year" in {
      val employmentWithoutStartDate = liveEndYearEmployment.copy(startDate = None)
      val filledEmployments = testEmploymentHistoryService.addFillers(List(employmentWithoutStartDate), TaxYear.current)

      val isOrderedFirstInList = filledEmployments.head == employmentWithoutStartDate
      isOrderedFirstInList shouldBe true

      filledEmployments map isNoRecordEmployment shouldBe Seq(false, true)

      val gap = filledEmployments(1)
      gap.startDate shouldBe Some(TaxYear.current.starts)
      gap.endDate shouldBe oneDayBefore(employmentWithoutStartDate.endDate)
    }

    "when original employment has no start date but has an end date, and the employment finishes on the first day of the tax year" in {
      val employmentWithoutStartDate = liveOngoingEmployment.copy(startDate = None, endDate = Some(TaxYear.current.starts))
      val filledEmployments = testEmploymentHistoryService.addFillers(List(employmentWithoutStartDate), TaxYear.current)

      val isOrderedFirstInList = filledEmployments.head == employmentWithoutStartDate
      isOrderedFirstInList shouldBe true

      filledEmployments map isNoRecordEmployment shouldBe Seq(false, true)

      val gap = filledEmployments(1)
      gap.startDate shouldBe oneDayAfter(employmentWithoutStartDate.endDate)
      gap.endDate shouldBe None
    }

    "when original employment has no start date but has an end date, and there is another employment immediately afterwards" in {
      val employmentWithoutStartDate = liveMidYearEmployment.copy(startDate = None)
      val subsequentEmployment = liveMidYearEmployment.copy(
        startDate = oneDayAfter(liveMidYearEmployment.endDate),
        endDate = None
      )
      val filledEmployments = testEmploymentHistoryService.addFillers(List(employmentWithoutStartDate, subsequentEmployment), TaxYear.current)

      val isOrderedFirstInList = filledEmployments.head == employmentWithoutStartDate
      isOrderedFirstInList shouldBe true

      filledEmployments map isNoRecordEmployment shouldBe Seq(false, true, false)

      val gap = filledEmployments(1)
      gap.startDate shouldBe Some(TaxYear.current.starts)
      gap.endDate shouldBe oneDayBefore(employmentWithoutStartDate.endDate)
    }

    "when original employment has no start date but has an end date, and there is another employment some days later" in {
      val employmentWithoutStartDate = liveMidYearEmployment.copy(startDate = None)
      val subsequentEmployment = liveMidYearEmployment.copy(
        startDate = liveMidYearEmployment.endDate.map(_.plusDays(3)),
        endDate = None
      )
      val filledEmployments = testEmploymentHistoryService.addFillers(List(employmentWithoutStartDate, subsequentEmployment), TaxYear.current)

      val isOrderedFirstInList = filledEmployments.head == employmentWithoutStartDate
      isOrderedFirstInList shouldBe true

      filledEmployments map isNoRecordEmployment shouldBe Seq(false, true, true, false)

      val firstGap = filledEmployments(1)
      firstGap.startDate shouldBe Some(TaxYear.current.starts)
      firstGap.endDate shouldBe oneDayBefore(employmentWithoutStartDate.endDate)

      val secondGap = filledEmployments(2)
      secondGap.startDate shouldBe oneDayAfter(employmentWithoutStartDate.endDate)
      secondGap.endDate shouldBe oneDayBefore(subsequentEmployment.startDate)
    }

    "when original employment has no start date but has an end date, and there is another employment immediately before (its end date)" in {
      val preceedingEmployment = liveMidYearEmployment.copy(
        startDate = Some(TaxYear.current.starts),
        endDate = oneDayBefore(liveMidYearEmployment.endDate)
      )
      val employmentWithoutStartDate = liveMidYearEmployment.copy(startDate = None)

      val filledEmployments = testEmploymentHistoryService.addFillers(List(preceedingEmployment, employmentWithoutStartDate), TaxYear.current)

      val isOrderedFirstInList = filledEmployments.head == employmentWithoutStartDate
      isOrderedFirstInList shouldBe true

      filledEmployments map isNoRecordEmployment shouldBe Seq(false, false, true)

      val gap = filledEmployments(2)
      gap.startDate shouldBe oneDayAfter(employmentWithoutStartDate.endDate)
      gap.endDate shouldBe None
    }

    "when original employment has no start date but has an end date, and there is another employment some days before (its end date)" in {
      val preceedingEmployment = liveMidYearEmployment.copy(
        startDate = Some(TaxYear.current.starts),
        endDate = liveMidYearEmployment.endDate.map(_.minusDays(3))
      )
      val employmentWithoutStartDate = liveMidYearEmployment.copy(startDate = None)

      val filledEmployments = testEmploymentHistoryService.addFillers(List(preceedingEmployment, employmentWithoutStartDate), TaxYear.current)

      val isOrderedFirstInList = filledEmployments.head == employmentWithoutStartDate
      isOrderedFirstInList shouldBe true

      filledEmployments map isNoRecordEmployment shouldBe Seq(false, false, true, true)

      val firstGap = filledEmployments(2)
      firstGap.startDate shouldBe oneDayAfter(preceedingEmployment.endDate)
      firstGap.endDate shouldBe oneDayBefore(employmentWithoutStartDate.endDate)

      val secondGap = filledEmployments(3)
      secondGap.startDate shouldBe oneDayAfter(employmentWithoutStartDate.endDate)
      secondGap.endDate shouldBe None
    }

    "when original employment has no start date and has no end date, and there are no other employments" in {
      val employmentNoDates = liveOngoingEmployment.copy(startDate = None, endDate = None)

      val filledEmployments = testEmploymentHistoryService.addFillers(List(employmentNoDates), TaxYear.current)

      filledEmployments shouldBe List(employmentNoDates)
    }

    "when original employment has no start date and has no end date, and there is another employment" in {
      val employmentNoDates = liveOngoingEmployment.copy(startDate = None, endDate = None)

      val filledEmployments = testEmploymentHistoryService.addFillers(List(employmentNoDates, liveMidYearEmployment), TaxYear.current)

      val isOrderedFirstInList = filledEmployments.head == employmentNoDates
      isOrderedFirstInList shouldBe true

      filledEmployments map isNoRecordEmployment shouldBe Seq(false, false)
    }

    "when there are two employments without start dates but with end dates" in {
      val employment1NoDates = liveOngoingEmployment.copy(startDate = None, endDate = None)
      val employment2NoDates = liveOngoingEmployment.copy(startDate = None, endDate = None)

      val filledEmployments = testEmploymentHistoryService.addFillers(List(employment1NoDates, employment2NoDates), TaxYear.current)

      filledEmployments map isNoRecordEmployment shouldBe Seq(false, false)
    }
  }
}