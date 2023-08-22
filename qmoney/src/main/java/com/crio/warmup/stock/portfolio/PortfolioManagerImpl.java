
package com.crio.warmup.stock.portfolio;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;

import com.crio.warmup.stock.dto.AnnualizedReturn;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.PortfolioTrade;
import com.crio.warmup.stock.dto.TiingoCandle;
import com.crio.warmup.stock.exception.StockQuoteServiceException;
import com.crio.warmup.stock.quotes.StockQuotesService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.springframework.cglib.core.Local;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.web.client.RestTemplate;

public class PortfolioManagerImpl implements PortfolioManager {




  




  private RestTemplate restTemplate;
  private StockQuotesService stockQuotesService;


  // Caution: Do not delete or modify the constructor, or else your build will break!
  // This is absolutely necessary for backward compatibility
  protected PortfolioManagerImpl(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }


  public PortfolioManagerImpl(StockQuotesService stockQuotesService) {
    this.stockQuotesService = stockQuotesService;
  }


  public List<Candle> getStockQuote(String symbol , LocalDate startLocalDate , LocalDate endLocalDate) throws JsonProcessingException, StockQuoteServiceException {
    return stockQuotesService.getStockQuote(symbol, startLocalDate, endLocalDate);
  }




  //TODO: CRIO_TASK_MODULE_REFACTOR
  // 1. Now we want to convert our code into a module, so we will not call it from main anymore.
  //    Copy your code from Module#3 PortfolioManagerApplication#calculateAnnualizedReturn
  //    into #calculateAnnualizedReturn function here and ensure it follows the method signature.
  // 2. Logic to read Json file and convert them into Objects will not be required further as our
  //    clients will take care of it, going forward.

  // Note:
  // Make sure to exercise the tests inside PortfolioManagerTest using command below:
  // ./gradlew test --tests PortfolioManagerTest

  //CHECKSTYLE:OFF




  private Comparator<AnnualizedReturn> getComparator() {
    return Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed();
  }

  //CHECKSTYLE:OFF

  // TODO: CRIO_TASK_MODULE_REFACTOR
  //  Extract the logic to call Tiingo third-party APIs to a separate function.
  //  Remember to fill out the buildUri function and use that.


  

  protected String buildUri(String symbol, LocalDate startDate, LocalDate endDate) {
    String token = "b5230ba118144c0ba1e18b171ee906e1de6fdf6e";

    String uriTemplate = "https://api.tiingo.com/tiingo/daily/$SYMBOL/prices?" +
     "startDate=$STARTDATE&endDate=$ENDDATE&token=$APIKEY";

    String url = uriTemplate.replace("$APIKEY",token).replace("$SYMBOL",symbol).replace("$STARTDATE",startDate.toString()).replace("$ENDDATE",endDate.toString());

    return url;

  }


  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturn(List<PortfolioTrade> portfolioTrades,
      LocalDate endDate) throws StockQuoteServiceException {
        AnnualizedReturn annualizedReturn;
        List<AnnualizedReturn> annualizedReturns = new ArrayList<AnnualizedReturn>();
        
        for (int i = 0; i < portfolioTrades.size(); i++) {

          annualizedReturn  = getAnnualizedReturn(portfolioTrades.get(i),endDate);
          
          annualizedReturns.add(annualizedReturn);
        }
        Comparator<AnnualizedReturn> sortbyAnnReturn = Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed();
        Collections.sort(annualizedReturns,sortbyAnnReturn);
        
    return annualizedReturns;
  }


  public AnnualizedReturn getAnnualizedReturn(PortfolioTrade trade, LocalDate endLocalDate)   throws StockQuoteServiceException {
   AnnualizedReturn  annualizedReturn;
   String symbol = trade.getSymbol();
   LocalDate startLocalDate = trade.getPurchaseDate();

    try{
     
      List<Candle> stockStartToEndDate;
      stockStartToEndDate = getStockQuote(symbol, startLocalDate, endLocalDate);
      
      Candle stockStartDate = stockStartToEndDate.get(0);
      Candle stockLatest = stockStartToEndDate.get(stockStartToEndDate.size()-1);

      Double buyPrice = stockStartDate.getOpen();
      Double sellPrice = stockLatest.getClose();

      Double totaReturn = (sellPrice-buyPrice) / buyPrice;

       //calculate years 
    Double calc_years = (double) ChronoUnit.DAYS.between(startLocalDate, endLocalDate) / 365;
    
    //annualized returns
    Double annualizedReturns = Math.pow(1 + totaReturn, 1.0 / calc_years) - 1;
    
    annualizedReturn = new AnnualizedReturn(symbol, annualizedReturns, totaReturn);
    
    }
    catch (JsonProcessingException e){
      annualizedReturn = new AnnualizedReturn(symbol, Double.NaN, Double.NaN);
    }

    return annualizedReturn;
    
    
    }


    @Override
  public List<AnnualizedReturn> calculateAnnualizedReturnParallel(
      List<PortfolioTrade> portfolioTrades, LocalDate endDate, int numThreads)
      throws StockQuoteServiceException {

    ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
    List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
    List<AnnualizedReturnTask> annualizedReturnTaskList = new ArrayList<>();
    List<Future<AnnualizedReturn>> annualizedReturnFutureList = null;
    for (PortfolioTrade portfolioTrade : portfolioTrades)
      annualizedReturnTaskList
          .add(new AnnualizedReturnTask(portfolioTrade, stockQuotesService, endDate));
    try {
      annualizedReturnFutureList = executorService.invokeAll(annualizedReturnTaskList);
    } catch (InterruptedException e) {
      throw new StockQuoteServiceException(e.getMessage());
    }
    for (Future<AnnualizedReturn> annualizedReturnFuture : annualizedReturnFutureList) {
      try {
        annualizedReturns.add(annualizedReturnFuture.get());
      } catch (InterruptedException | ExecutionException e) {
        throw new StockQuoteServiceException(e.getMessage());
      }
    }
    executorService.shutdown();
    return annualizedReturns.stream().sorted(getComparator()).collect(Collectors.toList());
  }
}
    
    







  

