package routing;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import DataHandler.ChoosePhoto.PhotoGroupSequence;
import DataHandler.Constant;
import DataHandler.GenerateScenario;
import DataHandler.Metadata;
import DataHandler.NodesFromDataset;
import DataHandler.PhotoPOI;
import DataHandler.SmartPhoto.PhotoSelectionSequence;
import core.Connection;
import core.DTNHost;
import core.DTNSim;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimClock;
import report.PhotoReport;
import routing.PhotoRouter;
import routinghandler.PhotoRouter_Interest;
import routinghandler.PhotoRouter_Choose;
import routinghandler.PhotoRouter_RAware;
import routinghandler.PhotoRouter_NoLimit;
import routinghandler.PhotoRouter_PhotoNet;
import routinghandler.PhotoRouter_Simple;
import routinghandler.PhotoRouter_SmartPhoto;

public class PhotoRouter extends ActiveRouter {
	
	private static List<PhotoRouter> allRouters;

	static {
		DTNSim.registerForReset(PhotoRouter.class.getCanonicalName());
		reset();
	}
	public static void reset() {
		allRouters = new ArrayList<PhotoRouter>();
	}
	@Override
	public PhotoRouter replicate() {
		return new PhotoRouter(this);
	}
	/*
	 * 
	 */
	public class MessageStatus {
		public static final int nothing_sent = 0;
		public static final int metadata_sent = 1;
		public static final int sequence_sent = 2;
		public static final int photo_sent = 3;
		
		public int sent;
		public boolean rcvdMetadata;
		public boolean rcvdSequence;
		public ArrayList<Integer> tempRcvdPhotoIdCache;
		
		public MessageStatus () {
			sent = nothing_sent;
			rcvdMetadata = false;
			rcvdSequence = false;
			tempRcvdPhotoIdCache = new ArrayList<Integer>();
		}
	}
	/*
	 * Router's property to be given in setting file 
	 */
	public static final String PHOTO_ROUTER = "PhotoRouter";
	public static final String Theta = "theta";
	public static final String Phi = "phi";
	public static final String POI = "poi";
	public static final String TPOI = "tpoi";
	public static final String Photo = "photo";
	public static final String Focus = "focus";
	public static final String WorldSize = "worldSize";
	public static final String NodeNum = "nodeNum";
	public static final String PhotoLimit = "photoLimit";
	public static final String ClusterRange = "clusterRange";
	public static final String DataSource = "dataSource";
	public static final String Scenario = "scenario";
	/*
	 * Routing wise variables
	 */
	public int theta;
	public int phi;
	private int poi;
	private int tpoi;
	private int photo;
	private int focus;
	private int lengthX;
	private int lengthY;
	public int nodeNum;
	public int photoLimit;
	private PhotoRouter_NoLimit nolimitPR;
	private PhotoRouter_Simple simplePR;
	private PhotoRouter_Choose choosePR;
	private PhotoRouter_Interest interestPR;
	private PhotoRouter_RAware rawarePR;
	private PhotoRouter_SmartPhoto smartPR;
	private PhotoRouter_PhotoNet photonetPR;
	/*
	 * Variables to store content
	 */
	public ArrayList<Metadata.POI> poiList;
	public ArrayList<Metadata.POI> hiddenPoiList;
	public ArrayList<Metadata.Photo> photoList;
	public Metadata metadata;
	public HashMap<Integer, MessageStatus> messageStatus;
	public HashMap<Integer, ArrayList<Metadata.Photo>> sendPhotoSeqMap;
	public HashMap<Integer, ArrayList<Metadata.Photo>> rcvdPhotoSeqMap;
	public HashMap<Integer, PhotoGroupSequence> sendPhotoGroupSeqMap;
	public HashMap<Integer, PhotoSelectionSequence> sendPhotoSelectionSeqMap;
	
	/*
	 * Scenario property
	 */
	public ArrayList<Metadata.UndefCluster> undefCluster;
	public int clusterRange;
	public int dataSource;
	public int scenario;
	/*
	 * 
	 */
	public PhotoRouter(Settings s) {
		super(s);

		Settings snwSettings = new Settings(PHOTO_ROUTER);	
		this.theta = snwSettings.getInt(Theta);
		this.phi = snwSettings.getInt(Phi);
		this.poi = snwSettings.getInt(POI);
		this.tpoi = snwSettings.getInt(TPOI);
		this.photo = snwSettings.getInt(Photo);
		this.focus = snwSettings.getInt(Focus);
		this.nodeNum = snwSettings.getInt(NodeNum);
		this.photoLimit = snwSettings.getInt(PhotoLimit);
		this.clusterRange = snwSettings.getInt(ClusterRange);
		this.dataSource = snwSettings.getInt(DataSource);
		this.scenario = snwSettings.getInt(Scenario);
		String[] worldSize = snwSettings.getSetting(WorldSize).split(",");
		this.lengthX = Integer.parseInt(worldSize[0].trim()) * 10; //convert to pixel (assumption)
		this.lengthY = Integer.parseInt(worldSize[1].trim()) * 10; //convert to pixel (assumption)
	}	
	/*
	 * 
	 */
	protected PhotoRouter(PhotoRouter r) {
		super(r);
		
		this.theta = r.theta;
		this.phi = r.phi;
		this.poi = r.poi;
		this.tpoi = r.tpoi;
		this.photo = r.photo;
		this.focus = r.focus;
		this.lengthX = r.lengthX;
		this.lengthY = r.lengthY;
		this.nodeNum = r.nodeNum;
		this.photoLimit = r.photoLimit;
		this.clusterRange = r.clusterRange;
		this.dataSource = r.dataSource;
		this.scenario = r.scenario;
		
		this.poiList = new ArrayList<Metadata.POI>();
		this.hiddenPoiList = new ArrayList<Metadata.POI>();
		this.photoList = new ArrayList<Metadata.Photo>();
		this.metadata = new Metadata(this.theta);
		this.messageStatus = new HashMap<Integer, MessageStatus>();
		this.sendPhotoSeqMap = new HashMap<Integer, ArrayList<Metadata.Photo>>();
		this.rcvdPhotoSeqMap = new HashMap<Integer, ArrayList<Metadata.Photo>>();
		this.sendPhotoGroupSeqMap = new HashMap<Integer, PhotoGroupSequence>();
		this.sendPhotoSelectionSeqMap = new HashMap<Integer, PhotoSelectionSequence>();
		this.undefCluster = new ArrayList<Metadata.UndefCluster>();
		
		allRouters.add(this);
	}
	/*
	 * 
	 */
	@Override
	public void init(DTNHost host, List<MessageListener> mListeners) {
		super.init(host, mListeners);
		
		if(this.getHost().getAddress() == 0) {
			GenerateScenario.Reset();
			NodesFromDataset.Reset();
		}
		
		GenerateScenario msgInfo = GenerateScenario.GetInstance(this.poi, this.tpoi-this.poi, this.theta, this.photo, this.lengthX, this.lengthY, this.focus, this.phi);
		if(this.getHost().getAddress() == 0) {
			PhotoReport.SetServerVariables(msgInfo);
		}
		NodesFromDataset nodesDataset =  NodesFromDataset.GetInstance(this.dataSource, this.photo, this.nodeNum);
		
		//fill all poi info
		int poiCounter = 0;
		for(PhotoPOI.POI poi: msgInfo.actualPoiList) {
			this.poiList.add(metadata.new POI(++poiCounter, metadata.new Point(poi.tloc.x, poi.tloc.y), poi.expired, poi.ts));
		}
		for(PhotoPOI.POI poi: msgInfo.hiddenPoiList) {
			this.hiddenPoiList.add(metadata.new POI(++poiCounter, metadata.new Point(poi.tloc.x, poi.tloc.y), poi.expired, poi.ts));
		}
		//fill initial taken photos
		if(getHost().toString().startsWith("v") == false) {
			//for(int i=0; i<msgInfo.photoList.size(); i++) {
				//if(i % nodeNum == getHost().getAddress()) {
				ArrayList<Integer> nodePhotoIndexes = nodesDataset.nodePhotoList[getHost().getAddress()];
				for(int i=0; i<nodePhotoIndexes.size(); i++) {
					int index = nodePhotoIndexes.get(i);
					PhotoPOI.Photo photo = msgInfo.photoList.get(index);
					
					this.photoList.add(metadata.new Photo(photo.id, photo.hid,
							metadata.new Point(photo.ploc.x, photo.ploc.y),
							photo.tid,
							photo.direction,
							photo.focus,
							true,
							false,	
							getHost().getAddress(),
							metadata.new Point(photo.pointDir.x, photo.pointDir.y),
							metadata.new Point(photo.pointFocus.x, photo.pointFocus.y),
							metadata.new Point(photo.point1.x, photo.point1.y),
							metadata.new Point(photo.point2.x, photo.point2.y)));
					//this.ownedPhoto++;
				}
			//}
			//initial metadata calculations for poi based on current photos
			metadata.SetInitialValues(poiList, photoList); //System.out.println(getHost().toString() + "_" + this.photoList.size());
		}
//		else {
//			System.out.println(this.getHost().getAddress());
//		}
	
		//initialize router types
		if(this.scenario == Constant.NoLimit) {
			nolimitPR = new PhotoRouter_NoLimit(this);
			nolimitPR.Initialize();
		}
		else if (this.scenario == Constant.Simple || this.scenario == Constant.SimpleCluster) {
			simplePR = new PhotoRouter_Simple(this);
			simplePR.Initialize();
		}
		else if (this.scenario == Constant.GroupCluster) {
			choosePR = new PhotoRouter_Choose(this);
			choosePR.Initialize();
		}
		else if (this.scenario == Constant.GroupInterest) {
			interestPR = new PhotoRouter_Interest(this);
			interestPR.totalPoiNum = msgInfo.actualPoiList.size() + msgInfo.hiddenPoiList.size();
			interestPR.Initialize();
		}
		else if (this.scenario == Constant.RAware) {
			rawarePR = new PhotoRouter_RAware(this);
			rawarePR.Initialize();
		}
		else if (this.scenario == Constant.SmartPhoto) {
			smartPR = new PhotoRouter_SmartPhoto(this);
			smartPR.Initialize();
		}
		else if (this.scenario == Constant.PhotoNet) {
			photonetPR = new PhotoRouter_PhotoNet(this);
			photonetPR.Initialize();
		}
	}
	/*
	 * 
	 */
	@Override 
	public boolean createNewMessage(Message msg) {

		//case 1: sender = relay, receiver = relay
		if(this.getHost().toString().startsWith("v") == false && msg.getTo().toString().startsWith("v") == false) {
			//if this is the metadata message
			if(msg.getId().startsWith("m")) {
				ArrayList<Metadata.Photo> clonedPhotoList = new ArrayList<Metadata.Photo>();
				for(Metadata.Photo photo: this.photoList) {
					clonedPhotoList.add(photo.clone());
				}
				msg.addProperty(Constant.PHOTO_LIST, clonedPhotoList);
				//for group cluster
				if(this.scenario == Constant.GroupCluster) {
					msg.addProperty(Constant.PHOTO_GROUP, choosePR.rcvdPhotoGroupSeq.clone());
				}
				//for group interest
				else if(this.scenario == Constant.GroupInterest) {
					msg.addProperty(Constant.PHOTO_GROUP, interestPR.rcvdPhotoGroupSeq.clone());
				}
				//for comparison
				else if( this.scenario == Constant.RAware) {
					msg.addProperty(Constant.LAMBDA, rawarePR.getLambda());
					msg.addProperty(Constant.PROBABILITY, rawarePR.destDeliveryProb());
				}
				else if( this.scenario == Constant.SmartPhoto) {
					msg.addProperty(Constant.PHOTO_SELECTION, smartPR.rcvdPhotoSelectionSeq.clone());
				}
				else if( this.scenario == Constant.PhotoNet) {
					msg.addProperty(Constant.PIVOTS, "pivots");
				}
			}		
			//if this is the sequence message
			else if(msg.getId().startsWith("s")) {
				msg.addProperty(Constant.PHOTO_REQ, sendPhotoSeqMap.get(msg.getTo().getAddress()));
			}		
			//if this is the photo message
			else if(msg.getId().startsWith("p")) {
				msg.addProperty(Constant.PHOTO_ID, msg.getId());
				if( this.scenario == Constant.RAware || this.scenario == Constant.PhotoNet) {
					int photoId = Integer.parseInt(msg.getId().split("_")[1]); 
					Metadata.Photo photo = metadata.FindPhotoByID(photoId, photoList);
					if(photo != null)
						msg.addProperty(Constant.PHOTO_CONTENT, photo.clone()); //so photo content may not be sent. handle in receiver
				}
			}		
		}
		
		//case 2: sender = relay, receiver = server
		else if(this.getHost().toString().startsWith("v") == false && msg.getTo().toString().startsWith("v") == true) {
			//if this is the metadata message
			if(msg.getId().startsWith("m")) {
				ArrayList<Metadata.Photo> clonedPhotoList = new ArrayList<Metadata.Photo>();
				for(Metadata.Photo photo: this.photoList) {
					clonedPhotoList.add(photo.clone());
				}
				msg.addProperty(Constant.PHOTO_LIST, clonedPhotoList);
			}		
			//if this is the photo message
			else if(msg.getId().startsWith("p")) {
				msg.addProperty(Constant.PHOTO_ID, msg.getId());
				if( this.scenario == Constant.RAware || this.scenario == Constant.PhotoNet) {
					int photoId = Integer.parseInt(msg.getId().split("_")[1]); 
					Metadata.Photo photo = metadata.FindPhotoByID(photoId, photoList);
					msg.addProperty(Constant.PHOTO_CONTENT, photo.clone());
				}
			}		
		}
		
		//case 3: sender = server, receiver = relay
		else if(this.getHost().toString().startsWith("v") == true && msg.getTo().toString().startsWith("v") == false) {
			//if this is the sequence message
			if(msg.getId().startsWith("s")) {
				msg.addProperty(Constant.PHOTO_REQ, sendPhotoSeqMap.get(msg.getTo().getAddress()));
				if(this.scenario == Constant.GroupCluster || this.scenario == Constant.GroupInterest) {
					msg.addProperty(Constant.PHOTO_GROUP, sendPhotoGroupSeqMap.get(msg.getTo().getAddress()));
				}
				else if (this.scenario == Constant.SmartPhoto) {
					msg.addProperty(Constant.PHOTO_SELECTION, sendPhotoSelectionSeqMap.get(msg.getTo().getAddress()));
				}
			}	
		}

		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		addToMessages(msg, true);
		return true;
	}
	/*
	 * 
	 */
	public void updateSelf(DTNHost to) {
		//case 1: sender = relay, receiver = relay
		if(this.getHost().toString().startsWith("v") == false && to.toString().startsWith("v") == false) {
			switch (messageStatus.get(to.getAddress()).sent) {
			
			//create messages for sending photo and poi metadata
			case MessageStatus.nothing_sent:
				Message metadataMsg = new Message(getHost(), to, "m_"+getHost()+"_"+to, Constant.OneMB);
				createNewMessage (metadataMsg);
				messageStatus.get(to.getAddress()).sent = MessageStatus.metadata_sent;
				break;
			
				//create messages for sending photo sequence
			case MessageStatus.metadata_sent:
				//first check whether you got metadata from other host
				if(messageStatus.get(to.getAddress()).rcvdMetadata == true) {
					Message sequenceMsg = new Message(getHost(), to, "s_"+getHost()+"_"+to, Constant.HalfMB);
					createNewMessage (sequenceMsg);
					messageStatus.get(to.getAddress()).sent = MessageStatus.sequence_sent;
				}
				break;
			
				//create actual photo message.
			case MessageStatus.sequence_sent:
				//first check whether you got photo sequence from other host
				if(messageStatus.get(to.getAddress()).rcvdSequence == true) {
					for(int i=0; i<rcvdPhotoSeqMap.get(to.getAddress()).size(); i++) {
						int id = rcvdPhotoSeqMap.get(to.getAddress()).get(i).pid;
						Message photoMsg = new Message(getHost(), to, "p_" + id + "_"+getHost()+"_"+to, Constant.TwoMB);
						createNewMessage (photoMsg);
					}
					PhotoReport.TransferredPhotoTime.put(SimClock.getTime(), rcvdPhotoSeqMap.get(to.getAddress()).size());
					messageStatus.get(to.getAddress()).sent = MessageStatus.photo_sent;
				}
				break;
			}
		}
		
		//case 2: sender = relay, receiver = server
		else if(this.getHost().toString().startsWith("v") == false && to.toString().startsWith("v") == true) {
			switch (messageStatus.get(to.getAddress()).sent) {
			
			//create messages for sending photo and poi metadata
			case MessageStatus.nothing_sent:
				Message metadataMsg = new Message(getHost(), to, "m_"+getHost()+"_"+to, Constant.OneMB);
				createNewMessage (metadataMsg);
				messageStatus.get(to.getAddress()).sent = MessageStatus.metadata_sent;
				break;
			
				//create actual photo message.
			case MessageStatus.metadata_sent:
				//first check whether you got photo sequence from other host
				if(messageStatus.get(to.getAddress()).rcvdSequence == true) {
					for(int i=0; i<rcvdPhotoSeqMap.get(to.getAddress()).size(); i++) {
						int id = rcvdPhotoSeqMap.get(to.getAddress()).get(i).pid;
						Message photoMsg = new Message(getHost(), to, "p_" + id + "_"+getHost()+"_"+to, Constant.TwoMB);
						createNewMessage (photoMsg);
					}
					PhotoReport.TransferredPhotoTime.put(SimClock.getTime(), rcvdPhotoSeqMap.get(to.getAddress()).size());
					messageStatus.get(to.getAddress()).sent = MessageStatus.photo_sent;
				}
				break;
			}
		}
		
		//case 3: sender = server, receiver = relay
		else if(this.getHost().toString().startsWith("v") == true && to.toString().startsWith("v") == false) {
			switch (messageStatus.get(to.getAddress()).sent) {
				
			//create messages for sending photo sequence
			case MessageStatus.nothing_sent:
				//first check whether you got metadata from other host
				if(messageStatus.get(to.getAddress()).rcvdMetadata == true) {
					Message sequenceMsg = new Message(getHost(), to, "s_"+getHost()+"_"+to, Constant.HalfMB);
					createNewMessage (sequenceMsg);
					messageStatus.get(to.getAddress()).sent = MessageStatus.sequence_sent;
				}
				break;
			}
		}
	}
	public void updateRAware(DTNHost to) {
		//case 1: sender = relay, receiver = relay
		if(this.getHost().toString().startsWith("v") == false && to.toString().startsWith("v") == false) {
			switch (messageStatus.get(to.getAddress()).sent) {
			
			//create messages for sending photo and poi metadata
			case MessageStatus.nothing_sent:
				Message metadataMsg = new Message(getHost(), to, "m_"+getHost()+"_"+to, Constant.OneMB);
				createNewMessage (metadataMsg);
				messageStatus.get(to.getAddress()).sent = MessageStatus.metadata_sent;
				break;
			
				//create messages for sending photo sequence
			case MessageStatus.metadata_sent:
				//first check whether you got metadata from other host
				if(messageStatus.get(to.getAddress()).rcvdMetadata == true) {
					Message sequenceMsg = new Message(getHost(), to, "s_"+getHost()+"_"+to, Constant.HalfMB);
					createNewMessage (sequenceMsg);
					messageStatus.get(to.getAddress()).sent = MessageStatus.sequence_sent;
				}
				else if(messageStatus.get(to.getAddress()).rcvdSequence == true) {
					for(int i=0; i<rcvdPhotoSeqMap.get(to.getAddress()).size(); i++) {
						int id = rcvdPhotoSeqMap.get(to.getAddress()).get(i).pid;
						Message photoMsg = new Message(getHost(), to, "p_" + id + "_"+getHost()+"_"+to, Constant.TwoMB);
						createNewMessage (photoMsg); //check photo existence in createmessage
					}
					PhotoReport.TransferredPhotoTime.put(SimClock.getTime(), rcvdPhotoSeqMap.get(to.getAddress()).size());
					messageStatus.get(to.getAddress()).sent = MessageStatus.photo_sent;
				}
				break;
			
				//create actual photo message.
			case MessageStatus.sequence_sent:
				//first check whether you got photo sequence from other host
				if(messageStatus.get(to.getAddress()).rcvdSequence == true) {
					for(int i=0; i<rcvdPhotoSeqMap.get(to.getAddress()).size(); i++) {
						int id = rcvdPhotoSeqMap.get(to.getAddress()).get(i).pid;
						Message photoMsg = new Message(getHost(), to, "p_" + id + "_"+getHost()+"_"+to, Constant.TwoMB);
						createNewMessage (photoMsg);
					}
					PhotoReport.TransferredPhotoTime.put(SimClock.getTime(), rcvdPhotoSeqMap.get(to.getAddress()).size());
					messageStatus.get(to.getAddress()).sent = MessageStatus.photo_sent;
				}
				break;
			}
		}
		
		//case 2: sender = relay, receiver = server
		else if(this.getHost().toString().startsWith("v") == false && to.toString().startsWith("v") == true) {
			switch (messageStatus.get(to.getAddress()).sent) {
			
			//create messages for sending photo and poi metadata
			case MessageStatus.nothing_sent:
				Message metadataMsg = new Message(getHost(), to, "m_"+getHost()+"_"+to, Constant.OneMB);
				createNewMessage (metadataMsg);
				messageStatus.get(to.getAddress()).sent = MessageStatus.metadata_sent;
				break;
			
				//create actual photo message.
			case MessageStatus.metadata_sent:
				//first check whether you got photo sequence from other host
				if(messageStatus.get(to.getAddress()).rcvdSequence == true) {
					for(int i=0; i<rcvdPhotoSeqMap.get(to.getAddress()).size(); i++) {
						int id = rcvdPhotoSeqMap.get(to.getAddress()).get(i).pid;
						Message photoMsg = new Message(getHost(), to, "p_" + id + "_"+getHost()+"_"+to, Constant.TwoMB);
						//if photo still there, only send the photomessage then
						Metadata.Photo photo = metadata.FindPhotoByID(id, photoList);
						if(photo != null) { //if photo is still there
							createNewMessage (photoMsg);
						}
					}
					PhotoReport.TransferredPhotoTime.put(SimClock.getTime(), rcvdPhotoSeqMap.get(to.getAddress()).size());
					messageStatus.get(to.getAddress()).sent = MessageStatus.photo_sent;
				}
				break;
			}
		}
		
		//case 3: sender = server, receiver = relay
		else if(this.getHost().toString().startsWith("v") == true && to.toString().startsWith("v") == false) {
			switch (messageStatus.get(to.getAddress()).sent) {
				
			//create messages for sending photo sequence
			case MessageStatus.nothing_sent:
				//first check whether you got metadata from other host
				if(messageStatus.get(to.getAddress()).rcvdMetadata == true) {
					Message sequenceMsg = new Message(getHost(), to, "s_"+getHost()+"_"+to, Constant.HalfMB);
					createNewMessage (sequenceMsg);
					messageStatus.get(to.getAddress()).sent = MessageStatus.sequence_sent;
				}
				break;
			}
		}
	}
	public void updatePhotoNet(DTNHost to) {
		//case 1: sender = relay, receiver = relay
		if(this.getHost().toString().startsWith("v") == false && to.toString().startsWith("v") == false) {
			switch (messageStatus.get(to.getAddress()).sent) {
			
			//create messages for sending photo and poi metadata
			case MessageStatus.nothing_sent:
				Message metadataMsg = new Message(getHost(), to, "m_"+getHost()+"_"+to, Constant.OneMB);
				createNewMessage (metadataMsg);
				messageStatus.get(to.getAddress()).sent = MessageStatus.metadata_sent;
				break;
			
				//create messages for sending photo sequence
			case MessageStatus.metadata_sent:
				//first check whether you got metadata from other host
				if(messageStatus.get(to.getAddress()).rcvdMetadata == true) {
					for(int i=0; i<sendPhotoSeqMap.get(to.getAddress()).size(); i++) {
						int id = sendPhotoSeqMap.get(to.getAddress()).get(i).pid;
						Message photoMsg = new Message(getHost(), to, "p_" + id + "_"+getHost()+"_"+to, Constant.TwoMB);
						createNewMessage (photoMsg);
					}
					PhotoReport.TransferredPhotoTime.put(SimClock.getTime(), sendPhotoSeqMap.get(to.getAddress()).size());
					messageStatus.get(to.getAddress()).sent = MessageStatus.photo_sent;
				}
				break;
			}
		}
		
		//case 2: sender = relay, receiver = server
		else if(this.getHost().toString().startsWith("v") == false && to.toString().startsWith("v") == true) {
			switch (messageStatus.get(to.getAddress()).sent) {
			
			//create messages for sending photo and poi metadata
			case MessageStatus.nothing_sent:
				Message metadataMsg = new Message(getHost(), to, "m_"+getHost()+"_"+to, Constant.OneMB);
				createNewMessage (metadataMsg);
				messageStatus.get(to.getAddress()).sent = MessageStatus.metadata_sent;
				break;
			
				//create actual photo message.
			case MessageStatus.metadata_sent:
				//first check whether you got photo sequence from other host
				if(messageStatus.get(to.getAddress()).rcvdSequence == true) {
					for(int i=0; i<rcvdPhotoSeqMap.get(to.getAddress()).size(); i++) {
						int id = rcvdPhotoSeqMap.get(to.getAddress()).get(i).pid;
						Metadata.Photo photo = metadata.FindPhotoByID(id, photoList);
						if(photo != null) {
							Message photoMsg = new Message(getHost(), to, "p_" + id + "_"+getHost()+"_"+to, Constant.TwoMB);
							createNewMessage (photoMsg);
						}
					}
					PhotoReport.TransferredPhotoTime.put(SimClock.getTime(), rcvdPhotoSeqMap.get(to.getAddress()).size());
					messageStatus.get(to.getAddress()).sent = MessageStatus.photo_sent;
				}
				break;
			}
		}
		
		//case 3: sender = server, receiver = relay
		else if(this.getHost().toString().startsWith("v") == true && to.toString().startsWith("v") == false) {
			switch (messageStatus.get(to.getAddress()).sent) {
				
			//create messages for sending photo sequence
			case MessageStatus.nothing_sent:
				//first check whether you got metadata from other host
				if(messageStatus.get(to.getAddress()).rcvdMetadata == true) {
					Message sequenceMsg = new Message(getHost(), to, "s_"+getHost()+"_"+to, Constant.HalfMB);
					createNewMessage (sequenceMsg);
					messageStatus.get(to.getAddress()).sent = MessageStatus.sequence_sent;
				}
				break;
			}
		}
	}
	@Override
	public void update() {
//		if((this.dataSource == Constant.Infocom && SimClock.getTime() > 337500 * (0.5)) ||
//				this.dataSource == Constant.Synthetic ||
//				this.dataSource == Constant.Geolife ||
//				this.dataSource == Constant.ASTURIES) {
		//System.out.println(SimClock.getTime());

			//the following code block is for creating necessary message
			List<Connection> connections = getConnections();
	
			for (Connection con : connections) {
				DTNHost to = con.getOtherNode(getHost());
	
				if(messageStatus.containsKey(to.getAddress()) == false) {
					messageStatus.put(to.getAddress(), new MessageStatus());
				}
				
				if(this.scenario == Constant.RAware) {
					updateRAware(to);
				}
				else if(this.scenario == Constant.PhotoNet) {
					updatePhotoNet(to);
				}
				else {
					updateSelf(to);
				}
			}
//		}
		cvgAndRedDistributionCalculation();
			
		//usual update operations
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // can't start a new transfer
		}
		
		// Try only the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer
		}
	}
	/*
	 * 
	 */
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		
		if (msg.getTo() == getHost()) {					
			if(messageStatus.containsKey(from.getAddress()) == false) {
				messageStatus.put(from.getAddress(), new MessageStatus());
			}
			
			if(this.scenario == Constant.NoLimit) {
				nolimitPR.messageTransferred(msg, from);
			}
			else if (this.scenario == Constant.Simple || this.scenario == Constant.SimpleCluster) {
				simplePR.messageTransferred(msg, from);
			}
			else if (this.scenario == Constant.GroupCluster) {
				choosePR.messageTransferred(msg, from);
			}
			else if (this.scenario == Constant.GroupInterest) {
				interestPR.messageTransferred(msg, from);
			}
			else if (this.scenario == Constant.RAware) {
				rawarePR.messageTransferred(msg, from);
			}
			else if (this.scenario == Constant.SmartPhoto) {
				smartPR.messageTransferred(msg, from);
			}
			else if (this.scenario == Constant.PhotoNet) {
				photonetPR.messageTransferred(msg, from);
			}
			
			if(msg.getId().startsWith("m") || msg.getId().startsWith("s")) {
				PhotoReport.TransferredMetadataCounter++;
			}
			else if(msg.getId().startsWith("p")) {
				PhotoReport.TransferredPhotoCounter++;
			}
		}
		
		return msg;
	}
	/*
	 * 
	 */
	@Override
	public void changedConnection(Connection con) { 
		//for comparison
		if( this.scenario == Constant.RAware) {
			rawarePR.changedConnection(con);
		}
		else if (this.scenario == Constant.GroupCluster) {
			choosePR.changedConnection(con);
		}
		else if (this.scenario == Constant.GroupInterest) {
			interestPR.changedConnection(con);
		}
		else if (this.scenario == Constant.SmartPhoto) {
			smartPR.changedConnection(con);
		}
		
		//a connection just dropped, delete all message and settings related to it
		if(con.isUp() == false) {
			
			DTNHost to = con.getOtherNode(getHost());
			if(messageStatus.containsKey(to.getAddress()) == true) {
				
				//delete all sent message
				switch (messageStatus.get(to.getAddress()).sent) {
				
				case MessageStatus.photo_sent:		
					try {
						for(int i=0; i<rcvdPhotoSeqMap.get(to.getAddress()).size(); i++) {
							int id = rcvdPhotoSeqMap.get(to.getAddress()).get(i).pid;
							if(hasMessage("p_" + id + "_"+getHost()+"_"+to))
								deleteMessage("p_" + id + "_"+getHost()+"_"+to, true);
						}
					} catch(Exception e) {}
					
				case MessageStatus.sequence_sent:
					if(hasMessage("s_"+getHost()+"_"+to))
						deleteMessage("s_"+getHost()+"_"+to, true);
				
				case MessageStatus.metadata_sent:
					if(hasMessage("m_"+getHost()+"_"+to))
						deleteMessage("m_"+getHost()+"_"+to, true);
					break;
				}
				
				//delete the record of messagestatus
				messageStatus.remove(to.getAddress());
				//delete the record of photo sequence sent
				sendPhotoSeqMap.remove(to.getAddress());
			}
		}
	}

	///*
	@Override
	public void transferDone(Connection con) { 
		DTNHost to = con.getOtherNode(getHost());		
		//case 2: sender = relay, receiver = server
		if(this.getHost().toString().startsWith("v") == false && to.toString().startsWith("v") == true) {
			if (con.isMessageTransferred()) {
				if(con.getMessage().getId().startsWith("p")) {
					//"p_" + id + "_"+getHost()+"_"+to
					String msgId = (String)con.getMessage().getProperty(Constant.PHOTO_ID);
					int photoId = Integer.parseInt(msgId.split("_")[1]); 
					Metadata.Photo photo = metadata.FindPhotoByID(photoId, photoList);
					if(photo != null) photo.delivered = true;
				}
			}
		}
	}
	//*/
	
	public long getUserTime( ) {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean( );
		return bean.isCurrentThreadCpuTimeSupported( ) ?
				bean.getCurrentThreadUserTime( ) : 0L;
	}
	/*
	 * 
	 */
	public void cvgAndRedDistributionCalculation() {
		double simTime = SimClock.getTime();
		
		if( (simTime == 2) ||
			(this.dataSource == Constant.Infocom && simTime % 33750 == 0) ||
			(this.dataSource == Constant.ASTURIES && simTime % 3162240 == 0) ||
			(this.dataSource == Constant.Geolife && simTime % 16038847 == 0) ) {
			
			if(PhotoReport.cvgAndRedMap.containsKey(simTime)) {
				return;
			}
			
			int totalNodeNum = 0;
			for (PhotoRouter router : allRouters)
				if (router.getHost().toString().startsWith("v") == false)
					totalNodeNum++;
				
			HashMap<Metadata.POI, ArrayList<Integer>> allPoiPhotoDirMap = new HashMap<Metadata.POI, ArrayList<Integer>>();
			int totalRedundancy = 0, totalCoverage = 0, allRouterPhotoNum = 0;
			ArrayList<Integer> routerCoverage = new ArrayList<Integer>();
			ArrayList<Integer> routerRedundancy = new ArrayList<Integer>();
			ArrayList<Integer> routerPhotoNum = new ArrayList<Integer>();
//			int[] routerCoverage = new int[totalNodeNum];
//			int[] routerRedundancy = new int[totalNodeNum];
//			int[] routerPhotoNum = new int[totalNodeNum];
			
			for (PhotoRouter router : allRouters) {
				if (router.getHost().toString().startsWith("v") == false) {
					HashMap<Metadata.POI, ArrayList<Integer>> poiPhotoDirMap = new HashMap<Metadata.POI, ArrayList<Integer>>();
					
//					if (this.scenario == Constant.RAware || this.scenario == Constant.PhotoNet) {
//						for (Metadata.Photo photo: router.photoList) {
//							Metadata.POI poi;
//							if (photo.tid < 0) {
//								poi = this.metadata.FindPoiByID(photo.hid, PhotoReport.hiddenPoiList);
//							}
//							else {
//								poi = this.metadata.FindPoiByID(photo.tid, PhotoReport.actualPoiList);
//							}
//							int lowerAngle = this.metadata.GetLowerDirAngle(poi, photo);
//							if (poiPhotoDirMap.containsKey(poi) == false) {
//								poiPhotoDirMap.put(poi, new ArrayList<Integer>());							
//							}
//							poiPhotoDirMap.get(poi).add(lowerAngle);
//						}
//					}
//					else {
						ArrayList<Metadata.Photo> existingPhotoList = (ArrayList<DataHandler.Metadata.Photo>) this.metadata.FilteredExistingPhoto(router.photoList);
						
						for (Metadata.Photo photo: existingPhotoList) {
							Metadata.POI poi;
							if (photo.tid < 0) {
								poi = this.metadata.FindPoiByID(photo.hid, PhotoReport.hiddenPoiList);
							}
							else {
								poi = this.metadata.FindPoiByID(photo.tid, PhotoReport.actualPoiList);
							}
							int lowerAngle = this.metadata.GetLowerDirAngle(poi, photo);
							if (poiPhotoDirMap.containsKey(poi) == false) {
								poiPhotoDirMap.put(poi, new ArrayList<Integer>());
							}
							poiPhotoDirMap.get(poi).add(lowerAngle);
						}
//					}
					
					int coverage = 0, redundancy = 0;
					int routerPhotoNumCount = 0;
					for (Entry<Metadata.POI, ArrayList<Integer>> entry : poiPhotoDirMap.entrySet()) { 
						Metadata.POI poi = entry.getKey();
						ArrayList<Integer> lowerAngleList = entry.getValue();
						if (allPoiPhotoDirMap.containsKey(poi) == false) {
							allPoiPhotoDirMap.put(poi, new ArrayList<Integer>());
						}
						allPoiPhotoDirMap.get(poi).addAll(lowerAngleList);
//						routerPhotoNum[router.getHost().getAddress()] += lowerAngleList.size();
						routerPhotoNumCount += lowerAngleList.size();
						coverage += this.metadata.TotalCoverage(lowerAngleList);
						redundancy += this.metadata.RedundantCoverage(lowerAngleList);
					}
					allRouterPhotoNum += routerPhotoNumCount;
					//if (routerPhotoNumCount > this.photoLimit) {
					//	System.out.println();
					//}
					routerPhotoNum.add(routerPhotoNumCount);
					routerCoverage.add(coverage);
					routerRedundancy.add(redundancy);
//					routerCoverage[router.getHost().getAddress()] = coverage;
//					routerRedundancy[router.getHost().getAddress()] = redundancy;
					totalCoverage += coverage;
					totalRedundancy += redundancy;
				}	
			}
			double avgCoverage = (double)totalCoverage / totalNodeNum;
			double avgRedundancy = (double)totalRedundancy / totalNodeNum;
			
			int aboveAvgCvgCount = 0, belowAvgCvgCount = 0;
			int aboveAvgRedCount = 0, belowAvgRedCount = 0;
			int aboveAvgCvgPhotoCount = 0, belowAvgCvgPhotoCount = 0;
			int aboveAvgRedPhotoCount = 0, belowAvgRedPhotoCount = 0;
			
			for (int i=0; i<routerRedundancy.size(); i++) {
				int cvg = routerCoverage.get(i);
				int red = routerRedundancy.get(i);
				
				if (cvg > avgCoverage) {
					aboveAvgCvgCount++;
					aboveAvgCvgPhotoCount += routerPhotoNum.get(i);
				}
				else {
					belowAvgCvgCount++;
					belowAvgCvgPhotoCount += routerPhotoNum.get(i);
				}
				
				if (red > avgRedundancy) {
					aboveAvgRedCount++;
					aboveAvgRedPhotoCount += routerPhotoNum.get(i);
				}
				else {
					belowAvgRedCount++;
					belowAvgRedPhotoCount += routerPhotoNum.get(i);
				}
			}
			int aboveAvgCvgPhoto = aboveAvgCvgPhotoCount / aboveAvgCvgCount;
			int belowAvgCvgPhoto = belowAvgCvgPhotoCount / belowAvgCvgCount;				
			int aboveAvgRedPhoto = aboveAvgRedPhotoCount / aboveAvgRedCount;
			int belowAvgRedPhoto = belowAvgRedPhotoCount / belowAvgRedCount;
	
			int networkCoverage = 0, networkRedundancy = 0;
			for (Entry<Metadata.POI, ArrayList<Integer>> entry : allPoiPhotoDirMap.entrySet()) { 
				networkCoverage += this.metadata.TotalCoverage(entry.getValue());
				networkRedundancy += this.metadata.RedundantCoverage(entry.getValue());
			}
			
			double avgPhotoNum = (double)allRouterPhotoNum / totalNodeNum; 
			int aboveAvgPhoto = 0, belowAvgPhoto = 0;
			for (int photoNum: routerPhotoNum) {
				if (photoNum > avgPhotoNum) aboveAvgPhoto++;
				else belowAvgPhoto++;
			}
			
			PhotoReport.CalculateRedundancy(simTime, avgPhotoNum, aboveAvgPhoto, belowAvgPhoto,
					avgCoverage, aboveAvgCvgCount, belowAvgCvgCount, aboveAvgCvgPhoto, belowAvgCvgPhoto, networkCoverage,
					avgRedundancy, aboveAvgRedCount, belowAvgRedCount, aboveAvgRedPhoto, belowAvgRedPhoto, networkRedundancy);
		}
	}
}