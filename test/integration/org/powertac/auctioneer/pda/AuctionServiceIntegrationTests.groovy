package org.powertac.auctioneer.pda

import grails.test.GrailsUnitTestCase
import org.powertac.common.command.ShoutDoCreateCmd
import org.powertac.common.Competition
import org.powertac.common.Broker
import org.powertac.common.Product
import org.powertac.common.enumerations.ProductType
import org.powertac.common.enumerations.BuySellIndicator
import org.powertac.common.enumerations.OrderType
import org.powertac.common.Shout
import org.powertac.common.enumerations.ModReasonCode
import org.powertac.common.Timeslot
import org.powertac.common.command.ShoutDoDeleteCmd
import org.powertac.common.command.ShoutDoUpdateCmd
import org.powertac.common.command.CashDoUpdateCmd
import org.powertac.common.command.PositionDoUpdateCmd
import org.powertac.common.TransactionLog
import org.powertac.common.enumerations.TransactionType

class AuctionServiceIntegrationTests extends GrailsUnitTestCase {

  def competition
  def sampleTimeslot
  def sampleProduct
  def sampleSeller
  def sampleBuyer
  ShoutDoCreateCmd sell1
  ShoutDoCreateCmd buy1
  Shout buyShout
  Shout buyShout2
  Shout sellShout

  def auctionService

  protected void setUp() {
    super.setUp()

    competition = new Competition(name: "sampleCompetition", enabled: true, current: true)
    assert (competition.save())
    sampleSeller = new Broker(userName: "SampleSeller", competition: competition)
    assert (sampleSeller.save())
    sampleBuyer = new Broker(userName: "SampleBuyer", competition: competition)
    assert (sampleBuyer.save())
    sampleProduct = new Product(competition: competition, productType: ProductType.Future)
    assert (sampleProduct.save())
    sampleTimeslot = new Timeslot(serialNumber: 1, competition: competition, enabled: true)
    assert (sampleTimeslot.save())
    sell1 = new ShoutDoCreateCmd(competition: competition, broker: sampleSeller, product: sampleProduct, timeslot: sampleTimeslot, buySellIndicator: BuySellIndicator.SELL, orderType: OrderType.LIMIT)
    buy1 = new ShoutDoCreateCmd(competition: competition, broker: sampleBuyer, product: sampleProduct, timeslot: sampleTimeslot, buySellIndicator: BuySellIndicator.BUY, orderType: OrderType.LIMIT)

    buyShout = new Shout(competition: competition, broker: sampleBuyer, product: sampleProduct, timeslot: sampleTimeslot, buySellIndicator: BuySellIndicator.BUY, orderType: OrderType.LIMIT, modReasonCode: ModReasonCode.INSERT, latest: true)
    buyShout2 = new Shout(competition: competition, broker: sampleBuyer, product: sampleProduct, timeslot: sampleTimeslot, buySellIndicator: BuySellIndicator.BUY, orderType: OrderType.LIMIT, modReasonCode: ModReasonCode.INSERT, latest: true)
    sellShout = new Shout(competition: competition, broker: sampleSeller, product: sampleProduct, timeslot: sampleTimeslot, buySellIndicator: BuySellIndicator.SELL, orderType: OrderType.LIMIT, modReasonCode: ModReasonCode.INSERT, latest: true)
  }

  protected void tearDown() {
    super.tearDown()
  }

  void testIncomingSellShoutDoCreateCmd() {
    sell1.limitPrice = 10.0
    sell1.quantity = 10.0
    auctionService.processShoutCreate(sell1)

    assertEquals(1, Shout.list().size())
    Shout s1 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 10.0)
      eq('quantity', 10.0)
    }
    assertNotNull s1
    assertEquals(ModReasonCode.INSERT, s1.modReasonCode)
    assertNotNull s1.shoutId
    assertNotNull s1.transactionId

  }

  void testIncomingBuyShoutDoCreateCmd() {
    buy1.limitPrice = 12.0
    buy1.quantity = 10.0
    auctionService.processShoutCreate(buy1)

    assertEquals(1, Shout.list().size())
    Shout b1 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 12.0)
      eq('quantity', 10.0)
    }
    assertNotNull b1
    assertEquals(ModReasonCode.INSERT, b1.modReasonCode)
    assertNotNull b1.shoutId
    assertNotNull b1.transactionId

  }

  void testIncomingBuyAndSellShoutDoCreateCmd() {
    sell1.limitPrice = 10.0
    sell1.quantity = 10.0
    auctionService.processShoutCreate(sell1)

    buy1.limitPrice = 12.0
    buy1.quantity = 10.0
    auctionService.processShoutCreate(buy1)

    assertEquals(2, Shout.list().size())

    Shout s1 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 10.0)
      eq('quantity', 10.0)
    }
    assertNotNull s1
    assertEquals(ModReasonCode.INSERT, s1.modReasonCode)
    assertNotNull s1.shoutId
    assertNotNull s1.transactionId

    Shout b1 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 12.0)
      eq('quantity', 10.0)
    }
    assertNotNull b1
    assertEquals(ModReasonCode.INSERT, b1.modReasonCode)
    assertNotNull b1.shoutId
    assertNotNull b1.transactionId
  }


  void testDeletionOfShout() {
    //init
    sell1.limitPrice = 10.0
    sell1.quantity = 10.0
    auctionService.processShoutCreate(sell1)

    assertEquals(1, Shout.list().size())
    Shout s1 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 10.0)
      eq('quantity', 10.0)
    }
    assertNotNull s1

    //action
    def delSell1 = new ShoutDoDeleteCmd(competition: competition, broker: sampleSeller, shoutId: s1.shoutId)
    auctionService.processShoutDelete(delSell1)

    //validate
    assertEquals(2, Shout.list().size())

    Shout oldS1 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 10.0)
      eq('quantity', 10.0)
      eq('latest', false)
    }
    assertNotNull oldS1
    assertEquals(s1.shoutId, oldS1.shoutId)

    Shout delS1 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 10.0)
      eq('quantity', 10.0)
      eq('latest', true)
    }
    assertNotNull delS1
    assertEquals(s1.shoutId, delS1.shoutId)
    assertNotNull delS1.transactionId
    assertNotSame(oldS1.transactionId, delS1.transactionId)
  }

  void testUpdateOfShout() {
    //init
    sell1.limitPrice = 10.0
    sell1.quantity = 10.0
    auctionService.processShoutCreate(sell1)

    assertEquals(1, Shout.list().size())
    Shout s1 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 10.0)
      eq('quantity', 10.0)
    }
    assertNotNull s1

    //action
    def updateSell1 = new ShoutDoUpdateCmd(competition: competition, broker: sampleSeller, shoutId: s1.shoutId, quantity: 20.0, limitPrice: 9.0)
    auctionService.processShoutUpdate(updateSell1)

    //validate
    assertEquals(3, Shout.list().size())

    Shout oldInsertedS1 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 10.0)
      eq('quantity', 10.0)
      eq('modReasonCode', ModReasonCode.INSERT)
      eq('latest', false)
    }
    assertNotNull oldInsertedS1
    assertEquals(s1.shoutId, oldInsertedS1.shoutId)

    Shout oldDeletedS1 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 10.0)
      eq('quantity', 10.0)
      eq('modReasonCode', ModReasonCode.DELETIONBYUSER)
      eq('latest', false)
    }
    assertNotNull oldDeletedS1
    assertEquals(s1.shoutId, oldDeletedS1.shoutId)
    assertNotSame(oldInsertedS1.transactionId, oldDeletedS1.transactionId)

    Shout updatedS1 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 9.0)
      eq('quantity', 20.0)
      eq('latest', true)
    }
    assertNotNull updatedS1
    assertEquals(ModReasonCode.MODIFICATION, updatedS1.modReasonCode)
    assertEquals(s1.shoutId, updatedS1.shoutId)
    assertNotNull updatedS1.transactionId
    assertNotSame(oldInsertedS1.transactionId, updatedS1.transactionId)
    assertNotSame(oldDeletedS1.transactionId, updatedS1.transactionId)

  }

  void testSettlementForBuyShout() {
    buyShout.executionQuantity = 100
    buyShout.executionPrice = 10

    def cashUpdate = auctionService.settleCashUpdate(buyShout)

    assert (cashUpdate instanceof CashDoUpdateCmd)
    assertEquals(sampleBuyer, cashUpdate.broker)
    assertEquals(competition, cashUpdate.competition)
    assertEquals(-buyShout.executionQuantity * buyShout.executionPrice, cashUpdate.relativeChange)
    assertEquals("org.powertac.auctioneer.pda", cashUpdate.origin)

    def posUpdate = auctionService.settlePositionUpdate(buyShout)
    assert (posUpdate instanceof PositionDoUpdateCmd)
    assertEquals(sampleBuyer, posUpdate.broker)
    assertEquals(competition, posUpdate.competition)
    assertEquals(buyShout.executionQuantity, posUpdate.relativeChange)
    assertEquals("org.powertac.auctioneer.pda", posUpdate.origin)
  }

  void testSettlementForSellShout() {
    sellShout.executionQuantity = 100
    sellShout.executionPrice = 10

    def cashUpdate = auctionService.settleCashUpdate(sellShout)

    assert (cashUpdate instanceof CashDoUpdateCmd)
    assertEquals(sampleSeller, cashUpdate.broker)
    assertEquals(competition, cashUpdate.competition)
    assertEquals(sellShout.executionQuantity * sellShout.executionPrice, cashUpdate.relativeChange)
    assertEquals("org.powertac.auctioneer.pda", cashUpdate.origin)

    def posUpdate = auctionService.settlePositionUpdate(sellShout)
    assert (posUpdate instanceof PositionDoUpdateCmd)
    assertEquals(sampleSeller, posUpdate.broker)
    assertEquals(competition, posUpdate.competition)
    assertEquals(-sellShout.executionQuantity, posUpdate.relativeChange)
    assertEquals("org.powertac.auctioneer.pda", posUpdate.origin)
  }

  void testCompleteAllocationOfSingleBuyShout() {
    //init
    buyShout.quantity = 50
    buyShout.limitPrice = 11
    buyShout.shoutId = "111"
    buyShout.transactionId = "tbd"
    buyShout.save()

    Map stat = [:]
    stat.price = 10
    stat.executableVolume = 200
    stat.transactionId = "123"
    BigDecimal aggregQuantityAllocated = 100

    //action
    List results = auctionService.allocateSingleShout(buyShout, aggregQuantityAllocated, stat)
    //validate
    assert (results[0] instanceof Shout)
    Shout returnedShout = (Shout) results[0]
    assertEquals(stat.price, returnedShout.executionPrice)
    assertEquals(buyShout.quantity, returnedShout.executionQuantity)
    assertEquals(ModReasonCode.EXECUTION, returnedShout.modReasonCode)
    assertEquals(stat.transactionId, returnedShout.transactionId)
    assertEquals("Matched by org.powertac.auctioneer.pda", returnedShout.comment)

    assert (results[1] instanceof BigDecimal)
    assertEquals(aggregQuantityAllocated + buyShout.quantity, results[1])

    assertEquals(2, Shout.list().size())

    Shout persistedShout = (Shout) Shout.findByLatestAndShoutId(true, buyShout.shoutId)
    assertEquals(stat.price, persistedShout.executionPrice)
    assertEquals(buyShout.quantity, persistedShout.executionQuantity)
    assertEquals(ModReasonCode.EXECUTION, persistedShout.modReasonCode)
    assertEquals(stat.transactionId, persistedShout.transactionId)
    assertEquals("Matched by org.powertac.auctioneer.pda", persistedShout.comment)
  }

  void testPartialAllocationOfSingleBuyShout() {
    //init
    buyShout.quantity = 100
    buyShout.limitPrice = 11
    buyShout.shoutId = "111"
    buyShout.transactionId = "tbd"
    buyShout.save()

    Map stat = [:]
    stat.price = 10
    stat.executableVolume = 100
    stat.transactionId = "123"
    BigDecimal aggregQuantityAllocated = 50

    //action
    List results = auctionService.allocateSingleShout(buyShout, aggregQuantityAllocated, stat)
    //validate
    assert (results[0] instanceof Shout)
    Shout returnedShout = (Shout) results[0]
    assertEquals(stat.price, returnedShout.executionPrice)
    assertEquals((stat.executableVolume - aggregQuantityAllocated), returnedShout.executionQuantity)
    assertEquals(buyShout.quantity - (stat.executableVolume - aggregQuantityAllocated), returnedShout.quantity)
    assertEquals(ModReasonCode.PARTIALEXECUTION, returnedShout.modReasonCode)
    assertEquals(stat.transactionId, returnedShout.transactionId)
    assertEquals("Matched by org.powertac.auctioneer.pda", returnedShout.comment)

    assert (results[1] instanceof BigDecimal)
    assertEquals(stat.executableVolume, results[1])

    assertEquals(2, Shout.list().size())

    Shout persistedShout = (Shout) Shout.findByLatestAndShoutId(true, buyShout.shoutId)
    assertEquals(stat.price, persistedShout.executionPrice)
    assertEquals((stat.executableVolume - aggregQuantityAllocated), persistedShout.executionQuantity)
    assertEquals(buyShout.quantity - (stat.executableVolume - aggregQuantityAllocated), persistedShout.quantity)
    assertEquals(ModReasonCode.PARTIALEXECUTION, persistedShout.modReasonCode)
    assertEquals(stat.transactionId, persistedShout.transactionId)
    assertEquals("Matched by org.powertac.auctioneer.pda", persistedShout.comment)
  }

  void testSimpleUniformPriceCalculation() {
    //init
    sell1.limitPrice = 11.0
    sell1.quantity = 20.0
    auctionService.processShoutCreate(sell1)

    buy1.limitPrice = 11.0
    buy1.quantity = 10.0
    auctionService.processShoutCreate(buy1)

    //action
    def shouts = Shout.list()
    Map stat = auctionService.calcUniformPrice(shouts)

    //validate
    assertEquals(11.0, stat.price)
    assertEquals(10.0, stat.executableVolume)
    assertEquals(20.0, stat.aggregatedQuantityAsk)
    assertEquals(10.0, stat.aggregatedQuantityBid)
  }

  void testUniformPriceCalculationWithHigherMinimumAskQuantityAtLowestPrice() {
    //init
    sell1.limitPrice = 11.0
    sell1.quantity = 20.0
    auctionService.processShoutCreate(sell1)

    sell1.limitPrice = 13.0
    sell1.quantity = 50.0
    auctionService.processShoutCreate(sell1)

    buy1.limitPrice = 13.0
    buy1.quantity = 10.0
    auctionService.processShoutCreate(buy1)

    buy1.limitPrice = 11.0
    buy1.quantity = 70.0
    auctionService.processShoutCreate(buy1)

    //action
    def shouts = Shout.list()
    Map stat = auctionService.calcUniformPrice(shouts)

    //validate
    assertEquals(11.0, stat.price)
    assertEquals(20.0, stat.executableVolume)
    assertEquals(20.0, stat.aggregatedQuantityAsk)
    assertEquals(80.0, stat.aggregatedQuantityBid)
  }

  void testUniformPriceCalculationWithHighestMinimumAskQuantityAndMinimalSurplus() {
    //init
    sell1.limitPrice = 11.0
    sell1.quantity = 20.0
    auctionService.processShoutCreate(sell1)

    sell1.limitPrice = 13.0
    sell1.quantity = 50.0
    auctionService.processShoutCreate(sell1)

    buy1.limitPrice = 13.0
    buy1.quantity = 10.0
    auctionService.processShoutCreate(buy1)

    buy1.limitPrice = 12.0
    buy1.quantity = 30.0
    auctionService.processShoutCreate(buy1)

    buy1.limitPrice = 11.0
    buy1.quantity = 40.0
    auctionService.processShoutCreate(buy1)

    //action
    def shouts = Shout.list()
    Map stat = auctionService.calcUniformPrice(shouts)

    //validate
    assertEquals(12.0, stat.price)
    assertEquals(20.0, stat.executableVolume)
    assertEquals(20.0, stat.aggregatedQuantityAsk)
    assertEquals(40.0, stat.aggregatedQuantityBid)
  }


  void testUniformPriceCalculationWithHighestMinimumBidQuantityAtMiddlePrice() {
    //init
    sell1.limitPrice = 11.0
    sell1.quantity = 20.0
    auctionService.processShoutCreate(sell1)

    sell1.limitPrice = 12.0
    sell1.quantity = 30.0
    auctionService.processShoutCreate(sell1)

    sell1.limitPrice = 13.0
    sell1.quantity = 20.0
    auctionService.processShoutCreate(sell1)

    buy1.limitPrice = 13.0
    buy1.quantity = 10.0
    auctionService.processShoutCreate(buy1)

    buy1.limitPrice = 12.0
    buy1.quantity = 30.0
    auctionService.processShoutCreate(buy1)

    buy1.limitPrice = 11.0
    buy1.quantity = 40.0
    auctionService.processShoutCreate(buy1)

    //action
    def shouts = Shout.list()
    Map stat = auctionService.calcUniformPrice(shouts)

    //validate
    assertEquals(12.0, stat.price)
    assertEquals(40.0, stat.executableVolume)
    assertEquals(50.0, stat.aggregatedQuantityAsk)
    assertEquals(40.0, stat.aggregatedQuantityBid)
  }


  void testSimpleMarketClearing() {
    //init
    sell1.limitPrice = 11.0
    sell1.quantity = 20.0
    auctionService.processShoutCreate(sell1)

    buy1.limitPrice = 11.0
    buy1.quantity = 10.0
    auctionService.processShoutCreate(buy1)

    assertEquals(2, Shout.list().size())
    //action
    List results = auctionService.clearMarket()

    // Validate persisted obejcts
    assertEquals(4, Shout.list().size())

    Shout s1 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 11.0)
      eq('quantity', 20.0)
      eq('modReasonCode', ModReasonCode.INSERT)
    }
    assertNotNull(s1)
    assertFalse(s1.latest)
    assertEquals(BuySellIndicator.SELL, s1.buySellIndicator)

    Shout s2 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 11.0)
      eq('quantity', 10.0)
      eq('modReasonCode', ModReasonCode.INSERT)
    }
    assertNotNull(s2)
    assertFalse(s2.latest)
    assertEquals(BuySellIndicator.BUY, s2.buySellIndicator)

    Shout s3 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 11.0)
      eq('quantity', 10.0)
      eq('modReasonCode', ModReasonCode.PARTIALEXECUTION)
    }
    assertNotNull(s3)
    assert (s3.latest)
    assertEquals(BuySellIndicator.SELL, s3.buySellIndicator)
    assertEquals(10.0, s3.executionQuantity)
    assertEquals(11.0, s3.executionPrice)

    Shout s4 = (Shout) Shout.withCriteria(uniqueResult: true) {
      eq('limitPrice', 11.0)
      eq('quantity', 0.0)
      eq('modReasonCode', ModReasonCode.EXECUTION)
    }
    assertNotNull(s4)
    assert (s4.latest)
    assertEquals(BuySellIndicator.BUY, s4.buySellIndicator)
    assertEquals(10.0, s4.executionQuantity)
    assertEquals(11.0, s4.executionPrice)


    // Validate returned list

    CashDoUpdateCmd cashUpdateBuyer = results.findAll {it instanceof CashDoUpdateCmd && it.relativeChange <0}.first()
    assertEquals(sampleBuyer, cashUpdateBuyer.broker)
    assertEquals(competition, cashUpdateBuyer.competition)
    assertEquals(-110, cashUpdateBuyer.relativeChange)
    assertEquals("org.powertac.auctioneer.pda", cashUpdateBuyer.origin)

    PositionDoUpdateCmd posUpdateBuyer = results.findAll {it instanceof PositionDoUpdateCmd && it.relativeChange >0}.first()
    assert (posUpdateBuyer instanceof PositionDoUpdateCmd)
    assertEquals(sampleBuyer, posUpdateBuyer.broker)
    assertEquals(competition, posUpdateBuyer.competition)
    assertEquals(10, posUpdateBuyer.relativeChange)
    assertEquals("org.powertac.auctioneer.pda", posUpdateBuyer.origin)

    CashDoUpdateCmd cashUpdateSeller = results.findAll {it instanceof CashDoUpdateCmd && it.relativeChange >0}.first()
    assertEquals(sampleSeller, cashUpdateSeller.broker)
    assertEquals(competition, cashUpdateSeller.competition)
    assertEquals(+110, cashUpdateSeller.relativeChange)
    assertEquals("org.powertac.auctioneer.pda", cashUpdateSeller.origin)

    PositionDoUpdateCmd posUpdateSeller = results.findAll {it instanceof PositionDoUpdateCmd && it.relativeChange <0}.first()
    assertEquals(sampleSeller, posUpdateSeller.broker)
    assertEquals(competition, posUpdateSeller.competition)
    assertEquals(-10, posUpdateSeller.relativeChange)
    assertEquals("org.powertac.auctioneer.pda", posUpdateSeller.origin)

    TransactionLog quoteLog = results.findAll {it instanceof TransactionLog && it.transactionType == TransactionType.QUOTE}.first()
    assertEquals(11.0, quoteLog.ask)
    assertEquals(11.0, quoteLog.bid)
    assertEquals(20.0, quoteLog.askSize)
    assertEquals(10.0, quoteLog.bidSize)

    assertEquals(2, results.findAll{it instanceof Shout}.size())

    Shout updatedSell = results.findAll {it instanceof Shout && it.buySellIndicator == BuySellIndicator.SELL}.first()
    assertEquals(11.0, updatedSell.limitPrice)
    assertEquals(10.0, updatedSell.quantity)
    assertEquals(11.0, updatedSell.executionPrice)
    assertEquals(10.0, updatedSell.executionQuantity)
    assertEquals(ModReasonCode.PARTIALEXECUTION, updatedSell.modReasonCode)
    assertEquals(sampleSeller, updatedSell.broker)

    Shout updatedBuy = results.findAll {it instanceof Shout && it.buySellIndicator == BuySellIndicator.BUY}.first()
    assertEquals(11.0, updatedBuy.limitPrice)
    assertEquals(0.0, updatedBuy.quantity)
    assertEquals(11.0, updatedBuy.executionPrice)
    assertEquals(10.0, updatedBuy.executionQuantity)
    assertEquals(ModReasonCode.EXECUTION, updatedBuy.modReasonCode)
    assertEquals(sampleBuyer, updatedBuy.broker)

  }


}