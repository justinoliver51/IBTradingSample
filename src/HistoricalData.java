public class HistoricalData 
{
	public int reqId;		//integer	The ticker ID of the request to which this bar is responding.
	public String date;		//String	The date-time stamp of the start of the bar. The format is determined by the reqHistoricalData() formatDate parameter.
	public double open;		//Double	The bar opening price.
	public double high;		//Double	The high price during the time covered by the bar.
	public double low;		//Double	The low price during the time covered by the bar.
	public double close;	//Double	The bar closing price.
	public int barCount;	//Integer	The bar count.
	public int volume;		//Integer	The volume during the time covered by the bar.
	public double WAP;		//Double	The weighted average price during the time covered by the bar.
	public boolean hasGaps;	//Boolean	Identifies whether or not there are gaps in the data.
	public double totalCashTradedInInterval; 	// The total amount of cash traded over the interval
	
	public HistoricalData(int newReqId, String newDate, double newOpen,
			double newHigh, double newLow, double newClose, int newVolume, int newCount,
			double newWAP, boolean newHasGaps)
	{
		reqId = newReqId;
		date = newDate;
		open = newOpen;
		high = newHigh;
		low = newLow;
		barCount = newCount;
		volume = newVolume;
		WAP = newWAP;
		hasGaps = newHasGaps;
		
		// Calculate the total amount of cash traded over the interval
		totalCashTradedInInterval = WAP * volume;
	}
}
