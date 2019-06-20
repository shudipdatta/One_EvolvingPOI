package DataHandler;

public class Constant {
	public static final int HalfMB = 1024*512;
	public static final int OneMB = 2 * HalfMB;
	public static final int TwoMB = 2 * OneMB;
	
	public static final String PHOTO_LIST = "photoList";
	public static final String PHOTO_REQ = "photoReq";
	public static final String PHOTO_GROUP = "photoGroup";
	public static final String PHOTO_SELECTION = "photoSelection";
	public static final String PHOTO_ID = "photoId";
	public static final String LAMBDA = "lambda";
	public static final String PROBABILITY = "probability";
	public static final String PHOTO_CONTENT = "photoContent";
	public static final String PIVOTS = "pivots";
	
	public static final String IF_ADDED = "ifAdded";
	public static final String IF_DELETED = "ifDeleted";
	
	public static final int CASE_R2R = 1;
	public static final int CASE_R2S = 2;
	public static final int CASE_S2R = 3;
	
	public static final double Alpha = 0; //heatmap
	public static final double Beta = 0.5; // * number of photo
	public static final double Gamma = 0.5;// * avg dist
	public static final double Phi = 0.5;  // * poi priority
	public static final double Threshold = 0.8; //threshold for lambda
	public static final double DelProb = 0.6; //threshold for delivery probability
	public static final int MinInterestNum = 3;//a node has interest on at least 5 pois
	
	//data sources
	public static final int Synthetic= 1;
	public static final int Infocom = 2;
	public static final int Geolife = 3; 
	public static final int ASTURIES = 4; 
	
	//scenarios
	public static final int NoLimit = 1; 
	public static final int Simple = 2;
	public static final int SimpleCluster = 3;
	public static final int GroupCluster = 4;
	public static final int GroupInterest = 5;
	public static final int RAware = 6;
	public static final int SmartPhoto = 7;
	public static final int PhotoNet = 8;
	
	public static final int Infinite = 100000;
	
	//public static HashMap<Integer, Integer> testCounter = new HashMap<Integer, Integer>();
	public static int test = 0;
}
