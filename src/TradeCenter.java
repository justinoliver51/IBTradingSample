import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class TradeCenter 
{
	private static IBTradingAPI tradingAPI;
	
	// CONSTANTS
	private final int SECONDS = 1000;
	private final String BUY = "BUY";
	private final String SELL = "SELL";
	private static final int TRADERPERCENTAGE = 100;
	private static final int MAX_LEVERAGE = 4;
	private final String VOLUME = "VOLUME";
	
	public TradeCenter()
	{
        // Initialize the trading API connection
        tradingAPI = new IBTradingAPI();
        tradingAPI.connect();
        
        // Subscribe to updates from my account
        tradingAPI.initializeAvailableFunds();
	}
	
	public String trade(String symbol)
	{
		
		// Determine if this trade could make a profit
		if(isTradeable(symbol) == false)
		{
			System.out.println("Trade could not be validated...");
			return "Trade could not be validated...";
		}
		else
		{
			System.out.println("The stock may be tradeable.");
		}
		
		String orderAction = "BUY";
		
		//placeOrder(orderAction, String symbol, int quantity, boolean isSimulation, OrderStatus orderStatus) 
		
		return null; // No error
	}
	
	private int getHistoricalData(String symbol, int durationInt, String durationStr, String endDateTime, String barSizeSetting, String whatToShow)
	{
		int tickerID = tradingAPI.subscribeToHistoricalData(symbol, endDateTime, durationStr, barSizeSetting, whatToShow);
		
		if(tickerID == -1)
			return tickerID;
		
		// Wait until we have received the market data
		while(tradingAPI.getHistoricalData(tickerID, durationInt) == null){};
		
		return tickerID;
	}
	
	public boolean isMarketOpen()
	{
		// If this is an invalid time of day, exit
		long MILLIS_AT_8_30_AM = (long) (8.5 * 60 * 60 * 1000);
		long MILLIS_AT_3_00_PM = (long) (15 * 60 * 60 * 1000);
		long MILLIS_PER_DAY = 24 * 60 * 60 * 1000;
		long MILLIS_TIMEZONE_DIFF = 6 * 60 * 60 * 1000;
		
		//Date now = Calendar.getInstance(TimeZone.getTimeZone("US/Central")).getTime();
		long timePortion = System.currentTimeMillis() % MILLIS_PER_DAY;
		timePortion = timePortion < MILLIS_TIMEZONE_DIFF ? (MILLIS_PER_DAY - (MILLIS_TIMEZONE_DIFF - timePortion)) : (timePortion - MILLIS_TIMEZONE_DIFF); 
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
		
		if( (timePortion < MILLIS_AT_8_30_AM) || (timePortion > MILLIS_AT_3_00_PM) || (dayOfWeek == 7) || (dayOfWeek == 1) )
		{
			System.out.println("Market is closed!");
			return false;
		}
		else
			return true;
	}
	
	// Determines if this trade may be profitable
	// This is based on my algorithm.  We need to know
	// if the amount of money that we expect will be
	// traded will move the market.  If so, then the
	// stock is expected to rise and is "tradeable".
	public boolean isTradeable(String symbol)
	{
		double totalCashTradedYesterday = 0;
		double totalCashTradedLastWeek = 0; 	// NOT USED IN THIS FUNCTION, BUT I'VE
												// INCLUDED TO SHOW HOW TO GET MORE THAN
												// ONE DAY
		double totalCashTradedLastMonth = 0; 	// Same as ^^^
		
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        Calendar cal = Calendar.getInstance();
        
        // Calculate the appropriate endDateTime
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        
        // If it is a Sunday or Monday, use Friday's date
        if(dayOfWeek == 1)
        	cal.add(Calendar.DATE, -2); 
        else if(dayOfWeek == 2)
        	cal.add(Calendar.DATE, -3); 
        // Use yesterday's date
        else 
        	cal.add(Calendar.DATE, -1);    
        	  
        String endDateTime = dateFormat.format(cal.getTime()) + " 15:00:00 CST";
		
		// Get the data over the last 30 days
		String barSizeSetting = IBTradingAPI.ONEDAY;
		String whatToShow = IBTradingAPI.TRADES;
		
		// Gets the VWAP of yesterday, last week, and last month
		String durationStr = IBTradingAPI.ONEDAYINTEGER + IBTradingAPI.DAYS;
		int tickerID = getHistoricalData(symbol, IBTradingAPI.ONEDAYINTEGER, durationStr, endDateTime, barSizeSetting, whatToShow);
		ArrayList<HistoricalData> historicalDataArray = tradingAPI.getHistoricalData(tickerID,IBTradingAPI.ONEDAYINTEGER);
		totalCashTradedYesterday = historicalDataArray.get(0).totalCashTradedInInterval;
		
		if(tickerID == -1)
			return false;
		
		durationStr = IBTradingAPI.ONEWEEKINTEGER + IBTradingAPI.DAYS;
		tickerID = getHistoricalData(symbol, IBTradingAPI.ONEWEEKINTEGER, durationStr, endDateTime, barSizeSetting, whatToShow);
		historicalDataArray = tradingAPI.getHistoricalData(tickerID,IBTradingAPI.ONEWEEKINTEGER);
		for(int i = 0; i < IBTradingAPI.ONEWEEKINTEGER; i++)
		{
			totalCashTradedLastWeek += historicalDataArray.get(i).totalCashTradedInInterval;
		}
		
		if(tickerID == -1)
			return false;
		
		durationStr = IBTradingAPI.ONEMONTHINTEGER + IBTradingAPI.DAYS;
		tickerID = getHistoricalData(symbol, IBTradingAPI.ONEMONTHINTEGER, durationStr, endDateTime, barSizeSetting, whatToShow);
		historicalDataArray = tradingAPI.getHistoricalData(tickerID,IBTradingAPI.ONEMONTHINTEGER);
		for(int i = 0; i < IBTradingAPI.ONEMONTHINTEGER; i++)
		{
			totalCashTradedLastMonth += historicalDataArray.get(i).totalCashTradedInInterval;
		}
		
		if(tickerID == -1)
			return false;
		
		// If the total amount of money thrown around is estimated to move the market, go ahead
		if(totalCashTradedYesterday < 999999999.9)  // FIXME: Change this to a value that you need
			return true;
		else
			return false;
	}
	
	// Initiates the trade with TWS
	// This algorithm assumes you will be purchasing
	// with both your simulation account and your
	// cash account.
	public String executeTrade(String symbol)
	{
		// Make the purchase
		boolean isSimulation = false;
		boolean simulationOnly = true;
		int quantity;
		double price;
		int totalCash;
		OrderStatus buyOrderStatus = null, sellOrderStatus = null;
		
		// Get the cash from our account and multiply by the maximum amount
		// of leverage we are provided from IB
		totalCash = ((int) tradingAPI.getAvailableFunds(isSimulation)) * MAX_LEVERAGE;
		
		// Wait until we have received the market data
		String marketData;
		if(isMarketOpen() == true)
			marketData = "LAST_PRICE";  
		else
			marketData = "CLOSE_PRICE";	// Should only be using this in debug
		
		int tickerID = tradingAPI.subscribeToMarketData(symbol);
		while(tradingAPI.getMarketData(tickerID, marketData) == null){};
		
		// Get the market price
		price = (Double) tradingAPI.getMarketData(tickerID, marketData);
		quantity = (int) (totalCash / price);

		if(quantity <= 0)
		{
			return "Invalid quantity, " + quantity;
		}
		
		// Place the order with cash account
		if(simulationOnly == false)
			buyOrderStatus = tradingAPI.placeOrder(BUY, symbol, quantity, isSimulation, null);
		
		if(buyOrderStatus == null && simulationOnly == false)
			return "Unable to connect to TWS...";
		
		// Make the purchase with the Simulator
		// If we are trading with our cash account, then we do not really care
		// if the simulation fails or not, so do not exit.
		isSimulation = true;
		if(simulationOnly == true)
		{
			buyOrderStatus = tradingAPI.placeOrder(BUY, symbol, quantity, isSimulation, null);  
			
			if(buyOrderStatus == null)
				return "Unable to connect to TWS...";
		}
		else
		{
			tradingAPI.placeOrder(BUY, symbol, quantity, isSimulation, null);
		}
		
		// Sleep for 300 seconds, then sell
		try
		{			
			int timeTilSell = 300;  
			boolean cashOnlyOrderFlag = false;
			
			// Check the desired information every second for 60 seconds
			for(int numSeconds = 0; numSeconds < timeTilSell; numSeconds++)
			{
				// Special case - no need to do any checks with the order status
				if(simulationOnly == true)
				{
					Thread.sleep( timeTilSell * SECONDS );
					break;
				}
				
				//System.out.println("orderID = " + buyOrderStatus.orderId + ", buyOrderStatus = " + buyOrderStatus.status);
				
				// If we were unable to buy due to the stock being not marginable
				// Then make sure we are buying without any leverage
				if( (buyOrderStatus.status.equalsIgnoreCase("Inactive") == true) && (cashOnlyOrderFlag == false) )
				{
					System.out.println("Order was rendered inactive, trying again with our total cash, " + totalCash);
					cashOnlyOrderFlag = true;
					
					// Make the trade using only cash (no leverage)
					isSimulation = false;
					int cash = totalCash / MAX_LEVERAGE;
					quantity = (int) (totalCash / price);
					buyOrderStatus = tradingAPI.placeOrder(BUY, symbol, quantity, isSimulation, null);
					
					if( (buyOrderStatus == null) || (buyOrderStatus.status == null) )
						return "Unable to connect to TWS...";
					
					// Sleep for the remaining time
					Thread.sleep((timeTilSell - numSeconds) * SECONDS);
					
					// We are done sleeping, sell!
					break;
				}
				
				// Sleep for one second
				Thread.sleep( 1 * SECONDS );
			}
			
			
			// If the order was unsuccessful, exit
			if( (simulationOnly == false) && ((buyOrderStatus == null) || (buyOrderStatus.status == null) 
					|| (buyOrderStatus.status.equalsIgnoreCase("Inactive") == true) 
					|| buyOrderStatus.status.equalsIgnoreCase("Cancelled") 
					|| buyOrderStatus.status.equalsIgnoreCase("PendingCancel")) )
			{
				return "We were unable to purchase - " + buyOrderStatus.status;
			}
			
			// Cancel the order if we have not purchased any stock within the time limit
			if(buyOrderStatus.filled == 0)
			{
				if(simulationOnly == true)
					isSimulation = true;
				else
					isSimulation = false;
				
				tradingAPI.cancelOrder(buyOrderStatus, isSimulation);
				return "Order canceled - we did not buy within the time limit";
			}
			// If we have not completed the order, complete it
			else if(buyOrderStatus.status.equalsIgnoreCase("Filled") == false)
			{
				if(simulationOnly == true)
					isSimulation = true;
				else
					isSimulation = false;
				
				tradingAPI.cancelOrder(buyOrderStatus, isSimulation);
				
				// Wait until we have either completed the order or it is cancelled
				while( (buyOrderStatus.status.equalsIgnoreCase("Cancelled") == false) &&
						(buyOrderStatus.status.equalsIgnoreCase("Filled") == false) ){};
						
				// Get the quantity to sell
				quantity = buyOrderStatus.filled;
			}
		}
		catch ( InterruptedException e )
		{
			System.out.println( "awakened prematurely" );
		}
		
		// Sell the stocks
		isSimulation = false;
		if(simulationOnly == false)
			sellOrderStatus = tradingAPI.placeOrder(SELL, symbol, quantity, isSimulation, null);
		
		if( (simulationOnly == false) && (sellOrderStatus == null) )
			return "Unable to connect to TWS...";
		
		// Sell the stocks over the simulator
		isSimulation = true;
		if(simulationOnly == true)
		{
			sellOrderStatus = tradingAPI.placeOrder(SELL, symbol, quantity, isSimulation, null); 
			
			if(sellOrderStatus == null)
				return "Unable to connect to TWS...";
		}
		else
			tradingAPI.placeOrder(SELL, symbol, quantity, isSimulation, null);  

		return null;
		
		
	}
}
