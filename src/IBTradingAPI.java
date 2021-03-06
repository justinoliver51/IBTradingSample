import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Scanner;

import javax.swing.JFrame;

import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.EWrapperMsgGenerator;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;

public class IBTradingAPI extends JFrame implements EWrapper
{
	private EClientSocket m_client = new EClientSocket(this);
	private EClientSocket m_client_simulation = new EClientSocket(this);
	
	public boolean  m_bIsFAAccount = false;
	private boolean m_disconnectInProgress = false;

	// Personal variables
	private static int orderID;	 // If this value is not updated, we may simply never get a response...
	private static int tickerID = 0; 
	private static HashMap<String,OrderStatus> orderStatusHashMap = new HashMap<String,OrderStatus>();
	private static HashMap<Integer,HashMap<String,Object>> marketDataHashMap = new HashMap<Integer,HashMap<String,Object>>();
	private static HashMap<Integer,ArrayList<HistoricalData>> historicalDataHashMap = new HashMap<Integer,ArrayList<HistoricalData>>();
	private static HashMap<Integer,HashMap<String,Object>> databaseHashMap = new HashMap<Integer,HashMap<String,Object>>();
	private static double totalCash = 0;
	private static double totalCashSimulation = 0;
	private static int dayTradesRemaining = -1;
	private static boolean purchasingFlag = false;
	
	// CONSTANT STRINGS
	// Location of orderID.txt
	private final String orderIDPath = "/Users/justinoliver/Desktop/Developer/WebExtensions/orderID.txt";
	
	// Request Historical Data Parameters
	// whatToShow
	public static final String TRADES 						= "TRADES"; 
	public static final String MIDPOINT 					= "MIDPOINT"; 
	public static final String BID 							= "BID"; 
	public static final String ASK 							= "ASK"; 
	public static final String BID_ASK 						= "BID_ASK"; 
	public static final String HISTORICAL_VOLATILITY 		= "HISTORICAL_VOLATILITY";
	public static final String OPTION_IMPLIED_VOLATILITY 	= "OPTION_IMPLIED_VOLATILITY";
			
	// barSizeSetting
	public static final String ONESECOND 		= "1 sec";	
	public static final String FIVESECONDS 		= "5 secs";
	public static final String FIFTEENSECONDS 	= "15 secs";
	public static final String THIRTYSECONDS 	= "30 secs";
	public static final String ONEMINUTE 		= "1 min";
	public static final String TWOMINUTES 		= "2 mins";
	public static final String THREEMINUTES 	= "3 mins";
	public static final String FIVEMINUTES 		= "5 mins";
	public static final String FIFTEENMINUTES 	= "15 mins";
	public static final String THIRTYMINUTES 	=  "30 mins";
	public static final String ONEHOUR 			= "1 hour";
	public static final String ONEDAY 			= "1 day";
	
	// durationString
	public static final String SECONDS 	= " S";
	public static final String DAYS 	= " D";
	public static final String WEEKS 	= " W";
	public static final String MONTHS 	= " M";
	public static final String YEARS 	= " Y";
	
	// The number of work days in the specified time frame
	public static final int ONEDAYINTEGER 	= 1;
	public static final int ONEWEEKINTEGER 	= 5;
	public static final int ONEMONTHINTEGER = 21;
	
	public static final String BUYORDERID = "BuyOrderID";
	
	
	public IBTradingAPI()
	{
		// Get the current orderID
		Scanner sc;
		try 
		{
			sc = new Scanner(new File(orderIDPath));
			orderID = sc.nextInt() + 100;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			orderID = 1000000;
		}
	}
	
	public synchronized void connect() 
	{
		m_bIsFAAccount = false;

        // connect to TWS
        m_disconnectInProgress = false;
        
        if(m_client_simulation.isConnected() == false)
        {
        	m_client_simulation.eConnect(null, 7496, 0);	// FIXME: This port number is specific to your account
            if (m_client_simulation.isConnected()) {
                System.out.println("Connected to the TWS server, simulation!");
            }
        }
        if(m_client.isConnected() == false)
        {
        	m_client.eConnect(null, 7495, 0);				// FIXME: This port number is specific to your account
            if (m_client.isConnected()) {
                System.out.println("Connected to the TWS server!");
            }
        }
    }
	
	public synchronized void disconnect() 
	{
        // disconnect from TWS
        m_disconnectInProgress = true;
        m_client.eDisconnect();
        m_client_simulation.eDisconnect();
    }
	
	public OrderStatus getOrderStatus(int orderId)
	{
		return orderStatusHashMap.get(Integer.toString(orderId));
	}

    public synchronized OrderStatus placeOrder(String orderAction, String symbol, int quantity, boolean isSimulation, OrderStatus orderStatus) 
    {
    	int orderId = orderStatus == null ? orderID : orderStatus.orderId;
    	
    	// Check parameters
    	if( (orderAction == null) || (symbol == null) || (quantity == 0) )
    	{
    		System.out.println("Invalid parameters to placeOrder");
    		return null;
    	}
    	
        Order order = new Order();
        Contract contract = new Contract();
        
        setDefaultsOrder(order);
        setDefaultsContract(contract);
        
        contract.m_symbol = symbol;
        order.m_totalQuantity = quantity;
        order.m_action = orderAction;
        
        // Connect to TWS
    	connect();
        if( ((m_client.isConnected() == false) && (isSimulation == false)) || ((m_client_simulation.isConnected() == false) && (isSimulation == true)) )
        {
        	System.out.println("Unable to connect to TWS...");
        	return null;
        }
        
        // Place order
        if(isSimulation)
        	m_client_simulation.placeOrder( orderId, contract, order );
        else
        	m_client.placeOrder( orderId, contract, order );
        
        // Do not make a new purchase until we have finished selling from our last order
        // FIXME: May want a timeout
        //while(orderAction.equalsIgnoreCase("BUY") && (purchasingFlag == true)){}
        
        // Set the purchasing flag which prevents any other orders to occur until this
        // one is complete
        purchasingFlag = true;
        
        // Log time
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss aa");
		Date date = new Date();
		System.out.println("Order number " + orderId + ", " + orderAction + " " + symbol 
				+ " placed at: " + dateFormat.format(date));
		
		// Add the new order to our hash map
		OrderStatus newOrder;
		if(orderStatus == null)
		{
			newOrder =  new OrderStatus();
			orderStatusHashMap.put(Integer.toString(orderID), newOrder);
			
			// Update the orderID for the next order
			orderID++;
		}
		// Otherwise, we are modifying an existing order
		else
		{
			newOrder = orderStatus;
		}
		
		return newOrder;
    }
    
    public void cancelOrder(OrderStatus order, boolean isSimulation)
    {
        // Cancel order
        if(isSimulation)
        	m_client_simulation.cancelOrder(order.orderId);
        else
        	m_client.cancelOrder(order.orderId);
    }
    
    @Override
    public void orderStatus( int orderId, String status, int filled, int remaining,
			 double avgFillPrice, int permId, int parentId,
			 double lastFillPrice, int clientId, String whyHeld) 
    {
		// Received order status
		String msg = EWrapperMsgGenerator.orderStatus(orderId, status, filled, remaining,
		avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
		
		// Get the date
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss aa");
		Date date = new Date();
		System.out.println(msg + " " + dateFormat.format(date));
		
		// 
		OrderStatus order;
		if(orderStatusHashMap.containsKey(Integer.toString(orderId)))
		{
			order = orderStatusHashMap.get(Integer.toString(orderId));
			order.updateOrder(orderId, status, filled, remaining,
					avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
		}
		else
		{
			System.out.println("Unknown order, orderID: " + orderId);
			order = new OrderStatus(orderId, status, filled, remaining,
				avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
			orderStatusHashMap.put(Integer.toString(orderId), order);
		}
		
		// make sure id for next order is at least orderId+1
		orderID++;
		
		// Set the orderID in the file
		try {
			PrintWriter writer = new PrintWriter(orderIDPath, "UTF-8");
			writer.println(orderID);
			writer.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
    
    void setDefaultsContract(Contract m_contract)
    {
        // Set useful contract fields
    	m_contract.m_secType = "STK";
    	m_contract.m_strike = 0;
    	m_contract.m_exchange = "SMART";
    	m_contract.m_primaryExch = "ISLAND"; 
    	m_contract.m_currency = "USD";
    	
    	// Set other fields to 0
    	m_contract.m_conId = 0;
    	m_contract.m_strike = 0.0;
    	m_contract.m_right = null;
    	m_contract.m_multiplier = null;
    	m_contract.m_localSymbol = null;
    	m_contract.m_includeExpired = false;
    	m_contract.m_secIdType = null;
    	m_contract.m_secId = null;
    }
    
    void setDefaultsOrder(Order m_order)
    {
        // set order fields
    	
    	// main order fields
        m_order.m_action = "BUY";
        m_order.m_orderType = "MKT";
        m_order.m_orderId = orderID;
        m_order.m_clientId = 0;
    }
    
    public synchronized int subscribeToMarketData(String symbol)
    {
    	Contract contract = new Contract();
    	String genericTicklist = null;
    	boolean snapshot = true;
    	
    	// Set up the contract
    	setDefaultsContract(contract);
    	contract.m_symbol = symbol;
    	
    	// Ensure we are connect to TWS
    	connect();
        if(m_client.isConnected() == false)
        {
        	System.out.println("Unable to connect to TWS...");
        	return -1;
        }
    	
    	// Request the market data
    	m_client.reqMktData(tickerID, contract, genericTicklist, snapshot);
    	
    	// Add a new hash map to market data for this stock
    	marketDataHashMap.put(tickerID, new HashMap<String,Object>());
    	
    	return tickerID++;
    }
    
    // 
    public synchronized int subscribeToHistoricalData(String symbol, String endDateTime, String durationStr, String barSizeSetting, String whatToShow)
    {
    	Contract contract = new Contract();
    	int useRTH = 1; 			// 0 - all data is returned even where the market in question was outside of its regular trading hours.
    								// 1 - only data within the regular trading hours is returned, even if the requested time span falls partially or completely outside of the RTH.
    	int formatDate = 1; 		// 1 - dates applying to bars returned in the format: yyyymmdd{space}{space}hh:mm:dd
    	
    	// Set up the contract
    	setDefaultsContract(contract);
    	contract.m_symbol = symbol;
    	//contract.m_marketDepthRows = rows;
    	
    	// Ensure we are connect to TWS
    	connect();
        if(m_client.isConnected() == false)
        {
        	System.out.println("Unable to connect to TWS...");
        	return -1;
        }
    	
    	// Get the market depth
    	m_client.reqHistoricalData(tickerID, contract, endDateTime, durationStr, barSizeSetting, whatToShow, useRTH, formatDate);
    	
    	// Add a new hash map to market data for this stock
    	historicalDataHashMap.put(tickerID, new ArrayList<HistoricalData>());
    	
    	return tickerID++;
    }
    
    public double getAvailableFunds(boolean isSimulation)
    {
    	if(isSimulation)
    		return totalCashSimulation;
    	else
    		return totalCash;
    }
    
    public int getNumberOfDayTrades()
    {
    	return dayTradesRemaining;
    }
    
    public Object getMarketData(int tickerId, String marketInfo)
    {
    	if(marketDataHashMap.get(tickerId) == null)
    		return null;
    	else if(marketDataHashMap.get(tickerId).get(marketInfo) == null)
    		return null;
    	else
    		return marketDataHashMap.get(tickerId).get(marketInfo);
    }
    
    public ArrayList<HistoricalData> getHistoricalData(int tickerId, int numberOfEntries)
    {
    	if(historicalDataHashMap.get(tickerId) == null)
    		return null;
    	else if(historicalDataHashMap.get(tickerId).size() < numberOfEntries)
    		return null;
    	else
    		return historicalDataHashMap.get(tickerId);
    }
    
    public HashMap<String,Object> initializeTradeInfo(int orderID)
    {
    	HashMap<String,Object> tradeInfo = databaseHashMap.get(orderID);
    	
    	// Initialize a new trade 
    	if(tradeInfo == null)
    		tradeInfo = databaseHashMap.put(orderID, new HashMap<String,Object>());
    	
    	return tradeInfo;
    }
    
    public void initializeAvailableFunds()
    {
    	m_client_simulation.reqAccountUpdates(true, "DU170967");
    	m_client.reqAccountUpdates(true, "U1257707");
    }
    
    public boolean isStillPurchasing()
    {
    	return purchasingFlag;
    }
    
	@Override
	public void error(Exception e) {
		System.out.println(EWrapperMsgGenerator.error(e));
	}

	@Override
	public void error(String str) {
		System.out.println(EWrapperMsgGenerator.error(str));
	}

	@Override
	public void error(int id, int errorCode, String errorMsg) {
		System.out.println(EWrapperMsgGenerator.error(id, errorCode, errorMsg));
	}

	@Override
	public void connectionClosed() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickPrice(int tickerId, int field, double price,
			int canAutoExecute) {
		//System.out.println(EWrapperMsgGenerator.tickPrice(tickerId, field, price, canAutoExecute));
		
		String marketInfo = null;
		if(field == 0)
			marketInfo = "BID_SIZE";
		else if(field == 1)
			marketInfo = "BID_PRICE";
		else if(field == 2)
			marketInfo = "ASK_PRICE";
		else if(field == 3)
			marketInfo = "ASK_SIZE";
		else if(field == 4)
			marketInfo = "LAST_PRICE";
		else if(field == 5)
			marketInfo = "LAST_SIZE";
		else if(field == 6)
			marketInfo = "HIGH";
		else if(field == 7)
			marketInfo = "LOW";
		else if(field == 8)
			marketInfo = "VOLUME";
		else if(field == 9)
			marketInfo = "CLOSE_PRICE";
		else if(field == 11)
			marketInfo = "ASK_OPTION_COMPUTATION";
		else if(field == 12)
			marketInfo = "LAST_OPTION_COMPUTATION";
		else if(field == 13)
			marketInfo = "MODEL_OPTION_COMPUTATION";
		else if(field == 14)
			marketInfo = "OPEN_TICK";
		else if(field == 15)
			marketInfo = "LOW_13_WEEK";
		else if(field == 16)
			marketInfo = "HIGH_13_WEEK";
		else if(field == 17)
			marketInfo = "LOW_26_WEEK";
		else if(field == 18)
			marketInfo = "HIGH_26_WEEK";
		else if(field == 19)
			marketInfo = "LOW_52_WEEK";
		else if(field == 20)
			marketInfo = "HIGH_52_WEEK";
		else if(field == 21)
			marketInfo = "AVG_VOLUME";
		else if(field == 22)
			marketInfo = "OPEN_INTEREST";
		else if(field == 23)
			marketInfo = "OPTION_HISTORICAL_VOL";
		else if(field == 24)
			marketInfo = "OPTION_IMPLIED_VOL";
		else if(field == 25)
			marketInfo = "OPTION_BID_EXCH";
		else if(field == 26)
			marketInfo = "OPTION_ASK_EXCH";
		else if(field == 27)
			marketInfo = "OPTION_CALL_OPEN_INTEREST";
		else if(field == 28)
			marketInfo = "OPTION_PUT_OPEN_INTEREST";
		else if(field == 29)
			marketInfo = "OPTION_CALL_VOLUME";
		else if(field == 30)
			marketInfo = "OPTION_PUT_VOLUME";
		else if(field == 31)
			marketInfo = "INDEX_FUTURE_PREMIUM";
		else if(field == 32)
			marketInfo = "BID_EXCH";
		else if(field == 33)
			marketInfo = "ASK_EXCH";
		else if(field == 34)
			marketInfo = "AUCTION_VOLUME";
		else if(field == 35)
			marketInfo = "AUCTION_PRICE";
		else if(field == 36)
			marketInfo = "AUCTION_IMBALANCE";
		else if(field == 37)
			marketInfo = "MARK_PRICE";
		else if(field == 38)
			marketInfo = "BID_EFP_COMPUTATION";
		else if(field == 39)
			marketInfo = "ASK_EFP_COMPUTATION";
		else if(field == 40)
			marketInfo = "LAST_EFP_COMPUTATION";
		else if(field == 41)
			marketInfo = "OPEN_EFP_COMPUTATION";
		else if(field == 42)
			marketInfo = "HIGH_EFP_COMPUTATION";
		else if(field == 43)
			marketInfo = "LOW_EFP_COMPUTATION";
		else if(field == 44)
			marketInfo = "CLOSE_EFP_COMPUTATION";
		else if(field == 45)
			marketInfo = "LAST_TIMESTAMP";
		else if(field == 46)
			marketInfo = "SHORTABLE";
		else if(field == 47)
			marketInfo = "FUNDAMENTAL_RATIOS";
		else if(field == 48)
			marketInfo = "RT_VOLUME";
		else if(field == 49)
			marketInfo = "HALTED";
		else if(field == 50)
			marketInfo = "BIDYIELD";
		else if(field == 51)
			marketInfo = "ASKYIELD";
		else if(field == 52)
			marketInfo = "LASTYIELD";
		else if(field == 53)
			marketInfo = "CUST_OPTION_COMPUTATION";
		else if(field == 54)
			marketInfo = "TRADE_COUNT";
		else if(field == 55)
			marketInfo = "TRADE_RATE";
		else if(field == 56)
			marketInfo = "VOLUME_RATE";
		else
			marketInfo = "UNKNOWN";
		
		marketDataHashMap.get(tickerId).put(marketInfo, price);
	}

	@Override
	public void tickSize(int tickerId, int field, int size) {
		String marketInfo = null;
		if(field == 0)
			marketInfo = "BID_SIZE";
		else if(field == 3)
			marketInfo = "ASK_SIZE";
		else if(field == 5)
			marketInfo = "LAST_SIZE";
		else if(field == 8)
			marketInfo = "VOLUME";
		else if(field == 21)
			marketInfo = "AVG_VOLUME";
		else if(field == 54)
			marketInfo = "TRADE_COUNT";
		else if(field == 55)
			marketInfo = "TRADE_RATE";
		else if(field == 56)
			marketInfo = "VOLUME_RATE";
		else
			return;
		
		marketDataHashMap.get(tickerId).put(marketInfo, size);
	}

	@Override
	public void tickOptionComputation(int tickerId, int field,
			double impliedVol, double delta, double optPrice,
			double pvDividend, double gamma, double vega, double theta,
			double undPrice) {
		//System.out.println(EWrapperMsgGenerator.tickOptionComputation(tickerId, field, impliedVol, delta, optPrice, pvDividend, gamma, vega, theta, undPrice));
	}

	@Override
	public void tickGeneric(int tickerId, int tickType, double value) {
		//System.out.println(EWrapperMsgGenerator.tickGeneric(tickerId, tickType, value));
	}

	@Override
	public void tickString(int tickerId, int tickType, String value) {
		System.out.println(EWrapperMsgGenerator.tickString(tickerId, tickType, value));
	}

	@Override
	public void tickEFP(int tickerId, int tickType, double basisPoints,
			String formattedBasisPoints, double impliedFuture, int holdDays,
			String futureExpiry, double dividendImpact, double dividendsToExpiry) {
		//System.out.println(EWrapperMsgGenerator.tickEFP(tickerId, tickType, basisPoints, formattedBasisPoints, impliedFuture, holdDays, futureExpiry, dividendImpact, dividendsToExpiry));
	}

	@Override
	public void openOrder(int orderId, Contract contract, Order order,
			OrderState orderState) {
		//System.out.println(EWrapperMsgGenerator.openOrder(orderId, contract, order, orderState));
	}

	@Override
	public void openOrderEnd() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateAccountValue(String key, String value, String currency,
			String accountName) {
		// TODO Auto-generated method stub
		String msg = EWrapperMsgGenerator.updateAccountValue(key, value, currency, accountName);
		String simulationAccount = ""; // FIXME: SimulationID.  Example: DU170965
		String cashAccount = ""; // FIXME: AccountID
		
		// Only get the information regarding the current funds in the account
		if(key.equalsIgnoreCase("AvailableFunds"))
		{
			double cash = Double.parseDouble(value);
			
			// Simulation account
			if(accountName.equalsIgnoreCase(simulationAccount))
				totalCashSimulation = cash;
			// Real money account
			else if(accountName.equalsIgnoreCase(cashAccount))
			{
				totalCash = cash;
				System.out.println(msg);
			}
		}
		else if(key.equalsIgnoreCase("DayTradesRemaining") && accountName.equalsIgnoreCase(cashAccount))
		{
			dayTradesRemaining = Integer.parseInt(value);
			System.out.println(msg);
		}
	}

	@Override
	public void updatePortfolio(Contract contract, int position,
			double marketPrice, double marketValue, double averageCost,
			double unrealizedPNL, double realizedPNL, String accountName) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateAccountTime(String timeStamp) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void accountDownloadEnd(String accountName) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void nextValidId(int orderId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void contractDetails(int reqId, ContractDetails contractDetails) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void bondContractDetails(int reqId, ContractDetails contractDetails) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void contractDetailsEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void execDetails(int reqId, Contract contract, Execution execution) {
		//System.out.println(EWrapperMsgGenerator.execDetails(reqId, contract, execution));
		// execution.m_shares
		// execution.m_orderId
		
		//System.out.println("Order, " + execution.m_orderId + " executed " + execution.m_cumQty);
		
		
		// FIXME: We want to wait until after both the 
		OrderStatus orderStatus = orderStatusHashMap.get(Integer.toString(execution.m_orderId));
		
		// If we have completed the order, give the signal
		if( ((orderStatus.status.equalsIgnoreCase("Cancelled") == true) ||
				(orderStatus.status.equalsIgnoreCase("Filled") == true) )
			&& execution.m_side.equalsIgnoreCase("SLD"))
		{
			purchasingFlag = false;
		}
	}

	@Override
	public void execDetailsEnd(int reqId) {
		//System.out.println(EWrapperMsgGenerator.execDetailsEnd(reqId));
	}

	@Override
	public void updateMktDepth(int tickerId, int position, int operation,
			int side, double price, int size) {
		// TODO Auto-generated method stub
		System.out.println("updateMktDepth " + EWrapperMsgGenerator.updateMktDepth(tickerId, position, operation, side, price, size));
		// At this time, what is the price going for, 2 numbers
	}

	@Override
	public void updateMktDepthL2(int tickerId, int position,
			String marketMaker, int operation, int side, double price, int size) {
		// TODO Auto-generated method stub
		// MARKET DEPTH!
		System.out.println("updateMktDepthL2 " + EWrapperMsgGenerator.updateMktDepthL2(tickerId, position, marketMaker, operation, side, price, size));
	}

	@Override
	public void updateNewsBulletin(int msgId, int msgType, String message,
			String origExchange) {
		// TODO Auto-generated method stub
		// MARKET DEPTH!
	}

	@Override
	public void managedAccounts(String accountsList) {
		// TODO Auto-generated method stub
		String msg = EWrapperMsgGenerator.managedAccounts(accountsList);
		//System.out.println(msg);
	}

	@Override
	public void receiveFA(int faDataType, String xml) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalData(int reqId, String date, double open,
			double high, double low, double close, int volume, int count,
			double WAP, boolean hasGaps) {
		// TODO Auto-generated method stub
		System.out.println(EWrapperMsgGenerator.historicalData(reqId, date, open, high, low, close, volume, count, WAP, hasGaps));
		
		// If this is the last entry and null, return
		if(WAP == -1.0)
			return;
		
		ArrayList<HistoricalData> historicalDataArray = historicalDataHashMap.get(reqId);
		historicalDataArray.add(new HistoricalData(reqId, date, open, high, low, close, volume, count, WAP, hasGaps));
		
		//
		//if(historicalDataArray.size() == 1)
	}

	@Override
	public void scannerParameters(String xml) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerData(int reqId, int rank,
			ContractDetails contractDetails, String distance, String benchmark,
			String projection, String legsStr) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerDataEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high,
			double low, double close, long volume, double wap, int count) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void currentTime(long time) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void fundamentalData(int reqId, String data) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deltaNeutralValidation(int reqId, UnderComp underComp) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickSnapshotEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void marketDataType(int reqId, int marketDataType) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void commissionReport(CommissionReport commissionReport) {
		// TODO Auto-generated method stub
		
	}
	
}
