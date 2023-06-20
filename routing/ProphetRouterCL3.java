///* 
// * Copyright 2010 Aalto University, ComNet
// * Released under GPLv3. See LICENSE.txt for details. 
// */
//package routing;
//
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//import core.Connection;
//import core.DTNHost;
//import core.Message;
//import core.Settings;
//import core.SimClock;
//import core.Tuple;
//import java.util.HashSet;
//import java.util.Set;
//import report.MessageStatsReport;
//
///**
// * Implementation of PRoPHET router as described in
// * <I>Probabilistic routing in intermittently connected networks</I> by Anders
// * Lindgren et al.
// */
//public class ProphetRouterCL3 extends ActiveRouter implements CVDetectionEngine {
//
//    /**
//     * delivery predictability initialization constant
//     */
//    public static final double P_INIT = 0.75;
//    /**
//     * delivery predictability transitivity scaling constant default value
//     */
//    public static final double DEFAULT_BETA = 0.25;
//    /**
//     * delivery predictability aging constant
//     */
//    public static final double GAMMA = 0.98;
//
//    /**
//     * Prophet router's setting namespace ({@value})
//     */
//    public static final String PROPHETCL3_NS = "ProphetRouterCL3";
//    /**
//     * Number of seconds in time unit -setting id ({@value}). How many seconds
//     * one time unit is when calculating aging of delivery predictions. Should
//     * be tweaked for the scenario.
//     */
//    public static final String SECONDS_IN_UNIT_S = "secondsInTimeUnit";
//
//    /**
//     * Transitivity scaling constant (beta) -setting id ({@value}). Default
//     * value for setting is {@link #DEFAULT_BETA}.
//     */
//    public static final String BETA_S = "beta";
//    /* Alpha for CL (@value) */
//    public static final String ALPHA_CL = "alphaCL";
//    /**
//     * minimum time for update new state (window) - setting id (@value)
//     */
//    public static final String STATE_UPDATE_INTERVAL_S = "stateInterval";
//    /**
//     * the value of nrof seconds in time unit -setting
//     */
//    private int secondsInTimeUnit;
//    /**
//     * value of beta setting
//     */
//    private double beta;
//
//    /**
//     * delivery predictabilities
//     */
//    private Map<DTNHost, Double> preds;
//    /**
//     * last delivery predictability update (sim)time
//     */
////    DEFAULT VALUE :
//    public static final double DEFAULT_ALPHA_CL = 0.5;
//    /**
//     * default value for state interval update
//     */
//    public static final double DEFAULT_STATE_UPDATE_INTERVAL = 300;
//
//    private double lastAgeUpdate;
//
//    private double eta; // threshold value for CL
//
//    private double alpha;
//    /**
//     * value of stateUpdateInterval setting
//     */
//    private double stateUpdateInterval;
//
////    public double ALPHA = 0.5;
//    //INISIALISASI YA
//    private int nrofdrops; // inisialisasi jumlah drop
//    private int nrofrec; // inisialisasi jumlah receive
//    private int nrofgen; // inisialisasi jumlah Gen
//    private double CL = 0.0;// inisialisasi CL  
//    private double ratio = 0;
////    double CLold = 0.0;
//    private Map<String, ACKTTL> receiptBuffer;
//
//    protected double msggenerationinterval = 300;
//    private Set<String> messageReadytoDelete;
//    /**
//     * a map to record information about a connection and its limit
//     */
//    private Map<Connection, Integer> conlimitmap;
//    /**
//     * to count msg limit for each connection
//     */
////    private int msglimit = 1;
//    /**
////     * default value for ai
////     */
////    public static final int DEFAULT_AI = 1;
////    /**
////     * default value for md
////     */
////    public static final double DEFAULT_MD = 0.2;
//    /**
//     * value of md setting
//     */
////    private double md;
////    /**
////     * value of ai setting
////     */
////    private int ai;
////    public static final String AI_S = "ai";
////    /**
////     * MD (multiplicative decrease) - setting id (@value)
////     */
////    public static final String MD_S = "md";
//    /**
//     * to record the last time of message creation
//     */
//    private double endtimeofmsgcreation = 0;
//    /**
//     * dummy variable to set the interval to count the new CL to detect the new
//     * state
//     */
//    private double LastUpdateTimeofState = 0;
//
//    /**
//     * needed for CV report
//     */
//    private List<CVandTime> clandtime;
//
//    /**
//     * Constructor. Creates a new message router based on the settings in the
//     * given Settings object.
//     *
//     * @param s The settings object
//     */
//    public ProphetRouterCL3(Settings s) {
//        super(s);
//        Settings prophetSettings = new Settings(PROPHETCL3_NS);
//        secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
//        if (prophetSettings.contains(BETA_S)) {
//            beta = prophetSettings.getDouble(BETA_S);
//        } else {
//            beta = DEFAULT_BETA;
//        }
//
////        if (prophetSettings.contains(AI_S)) {
////            ai = prophetSettings.getInt(AI_S);
////        } else {
////            ai = DEFAULT_AI;
////        }
////
////        if (prophetSettings.contains(MD_S)) {
////            md = prophetSettings.getDouble(MD_S);
////        } else {
////            md = DEFAULT_MD;
////        }
//
//        if (prophetSettings.contains(ALPHA_CL)) {
//            alpha = prophetSettings.getDouble(ALPHA_CL);
//        } else {
//            alpha = DEFAULT_ALPHA_CL;
//        }
//
//        if (prophetSettings.contains(STATE_UPDATE_INTERVAL_S)) {
//            stateUpdateInterval = prophetSettings.getDouble(STATE_UPDATE_INTERVAL_S);
//        } else {
//            stateUpdateInterval = DEFAULT_STATE_UPDATE_INTERVAL;
//        }
//        initPreds();
//        cltimelist();
////        this.receiptBuffer = new HashMap<>();
////        this.messageReadytoDelete = new HashSet<>();
//        receiptbuffer();
//        msgreadytodelete();
////        limitconmap();
//    }
//
//    /**
//     * Copyconstructor.
//     *
//     * @param r The router prototype where setting values are copied from
//     */
//    protected ProphetRouterCL3(ProphetRouterCL3 r) {
//        super(r);
//        this.secondsInTimeUnit = r.secondsInTimeUnit;
//        this.beta = r.beta;
//        this.nrofdrops = r.nrofdrops;
//        this.nrofgen = r.nrofgen;
//        this.nrofrec = r.nrofrec;
//        this.alpha = r.alpha;
//        this.stateUpdateInterval = r.stateUpdateInterval;
//        this.eta = r.eta;
//        this.CL = r.CL;
////        Thread t = new Thread(this);
////        t.start();
//        initPreds();
//        cltimelist();
////        this.receiptBuffer = new HashMap<>();
////        this.messageReadytoDelete = new HashSet<>();
////        limitconmap();
//        receiptbuffer();
//        msgreadytodelete();
//    }
//
//    /**
//     * Initializes predictability hash
//     */
//    private void initPreds() {
//        this.preds = new HashMap<DTNHost, Double>();
//    }
//
//    protected void cltimelist() {
//        this.clandtime = new ArrayList<CVandTime>();
//    }
//
//    protected void receiptbuffer() {
//        this.receiptBuffer = new HashMap<>();
//    }
//
//    protected void msgreadytodelete() {
//        this.messageReadytoDelete = new HashSet<>();
//    }
//
////    protected void limitconmap() {
////        this.conlimitmap = new HashMap<Connection, Integer>();
////    }
//
//    @Override
//    public void changedConnection(Connection con) {
//       if (con.isUp()) {
//          
//			connectionUp(con);
//			
//			/*peer's router */
//			DTNHost otherHost = con.getOtherNode(getHost());
// updateDeliveryPredFor(otherHost);
//			updateTransitivePreds(otherHost);
////			conlimitmap.put(con, this.msglimit);
//			
//
//			Collection<Message> thisMsgCollection = getMessageCollection();
//
//			ProphetRouterCL3 peerRouter = (ProphetRouterCL3) otherHost.getRouter();
////			exchangemsginformation();
//			Map<String, ACKTTL> peerRB = peerRouter.getReceiptBuffer();
//			for (Map.Entry<String, ACKTTL> entry : peerRB.entrySet()) {
//				if (!receiptBuffer.containsKey(entry.getKey())) {
//					receiptBuffer.put(entry.getKey(), entry.getValue());
//
//				}
//
//			}
//			for (Message m : thisMsgCollection) {
//				/** Delete message that have a receipt */
//				if (receiptBuffer.containsKey(m.getId())) {
//					messageReadytoDelete.add(m.getId());
//				}
//			}
//			// delete transferred msg
//			for (String m : messageReadytoDelete) {
//
//				deletemsg(m, false);
//			}
//
//			messageReadytoDelete.clear();
//		} else {
//			connectionDown(con);
//			DTNHost otherHost = con.getOtherNode(getHost());
//			ProphetRouterCL3 peerRouter = (ProphetRouterCL3) otherHost.getRouter();
//			/* record the peer's nrofdrops & nrofreps 
//			 * as otherNrofDrops & otherNrofReps  */
////			otherNrofDrops += peerRouter.getNrofDrops();
////			otherNrofReps += peerRouter.getNrofReps();
////			conlimitmap.remove(con);
//			messageReadytoDelete.clear();
//		}
//
//    }
//
//    public void deletemsg(String msgID, boolean dropchecking) {
//        if (isSending(msgID)) {
//            List<Connection> conList = getConnections();
//            for (Connection cons : conList) {
//                if (cons.getMessage() != null && cons.getMessage().getId() == msgID) {
//                    cons.abortTransfer();
//                    break;
//                }
//            }
//        }
//        deleteMessage(msgID, dropchecking);
//    }
//
//    /**
//     * Updates delivery predictions for a host.
//     * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
//     *
//     * @param host The host we just met
//     */
//    private void updateDeliveryPredFor(DTNHost host) {
//        double oldValue = getPredFor(host);
//        double newValue = oldValue + (1 - oldValue) * P_INIT;
//        preds.put(host, newValue);
//    }
//
//    /**
//     * Returns the current prediction (P) value for a host or 0 if entry for the
//     * host doesn't exist.
//     *
//     * @param host The host to look the P for
//     * @return the current P value
//     */
//    public double getPredFor(DTNHost host) {
//        ageDeliveryPreds(); // make sure preds are updated before getting
//        if (preds.containsKey(host)) {
//            return preds.get(host);
//        } else {
//            return 0;
//        }
//    }
//
//    /**
//     * Updates transitive (A->B->C) delivery predictions.      <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
//     * </CODE>
//     *
//     * @param host The B host who we just met
//     */
//    private void updateTransitivePreds(DTNHost host) {
//        MessageRouter otherRouter = host.getRouter();
//        assert otherRouter instanceof ProphetRouterCL3 : "PRoPHET only works "
//                + " with other routers of same type";
//
//        double pForHost = getPredFor(host); // P(a,b)
//        Map<DTNHost, Double> othersPreds
//                = ((ProphetRouterCL3) otherRouter).getDeliveryPreds();
//
//        for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
//            if (e.getKey() == getHost()) {
//                continue; // don't add yourself
//            }
//
//            double pOld = getPredFor(e.getKey()); // P(a,c)_old
//            double pNew = pOld + (1 - pOld) * pForHost * e.getValue() * beta;
//            preds.put(e.getKey(), pNew);
//        }
//    }
//
//    /**
//     * Ages all entries in the delivery predictions.
//     * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of time
//     * units that have elapsed since the last time the metric was aged.
//     *
//     * @see #SECONDS_IN_UNIT_S
//     */
//    private void ageDeliveryPreds() {
//        double timeDiff = (SimClock.getTime() - this.lastAgeUpdate)
//                / secondsInTimeUnit;
//
//        if (timeDiff == 0) {
//            return;
//        }
//
//        double mult = Math.pow(GAMMA, timeDiff);
//        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
//            e.setValue(e.getValue() * mult);
//        }
//
//        this.lastAgeUpdate = SimClock.getTime();
//    }
//    
//    @Override
//    public void update() {
//       super.update();
//		if ((SimClock.getTime() - LastUpdateTimeofState ) >= stateUpdateInterval) {
//
//			double newCL = calculateCL();
//			CVandTime nilaicl = new CVandTime(newCL, SimClock.getTime());
//			clandtime.add(nilaicl);
//                        System.out.println("NILAI CL : " + newCL);
////			if (this.oldstate == -1) {
////				oldstate = staterequirement(this.CL, newCL);
////				actionChosen = this.QL.GetAction(oldstate);
////				this.actionSelectionController(actionChosen);
////			} else {
////				int newstate = staterequirement(this.CL, newCL);
////				this.updateState(newstate);
////			}
//			this.CL = newCL;
//			LastUpdateTimeofState  = SimClock.getTime();
//			
//		}
//		if (!canStartTransfer() || isTransferring()) {
//			return; // nothing to transfer or is currently transferring
//		}
//
//		// try messages that could be delivered to final recipient
//		if (exchangeDeliverableMessages() != null) {
//			return;
//		}
//    }
//
//    /**
//     * Returns a map of this router's delivery predictions
//     *
//     * @return a map of this router's delivery predictions
//     */
//    private Map<DTNHost, Double> getDeliveryPreds() {
//        ageDeliveryPreds(); // make sure the aging is done
//        return this.preds;
//    }
//
//    public Map<String, ACKTTL> getReceiptBuffer() {
//        return receiptBuffer;
//    }
//
//   
//
//    
//
////    public boolean isFinalDest(Message m, DTNHost aHost) {
////        if (m.getTo().equals(aHost)) {
////            return true;
////        }
////        return m.getTo().equals(aHost);
////    }
////    
//
////
////    public int getNrofDrops() {
////        return this.nrOfDrops;
////    }
////
////    public int getNrofRec() {
////        return this.nrOfRec;
////    }
////
////    public int getNrofGen() {
////        return this.nrOfGen;
////    }
//    
//
//    
//
//  
//
//    @Override
//    protected int startTransfer(Message m, Connection con) {
//        int retVal;
//
//        if (!con.isReadyForTransfer()) {
//            return TRY_LATER_BUSY;
//        }
//
//        retVal = con.startTransfer(getHost(), m);
//        if (retVal == RCV_OK) { // started transfer
//            addToSendingConnections(con);
//        } else if (deleteDelivered && retVal == DENIED_OLD
//                && m.getTo() == con.getOtherNode(this.getHost())) {
//            /* final recipient has already received the msg -> delete it */
//            this.deleteMessage(m.getId(), false);
//        }
//
//        return retVal;
//    }
//    
//     @Override
//    protected boolean makeRoomForMessage(int size) {
//
//        if (size > this.getBufferSize()) {
//            return false; // message too big for the buffer
//        }
//        System.out.println("DROP COBA : " + nrofdrops);
//        int freeBuffer = this.getFreeBufferSize();
//        /* delete messages from the buffer until there's enough space */
//        while (freeBuffer < size) {
//            Message m = getOldestMessage(true); // don't remove msgs being sent
//
//            if (m == null) {
//                return false; // couldn't remove any more messages
//            }
//
//            /* delete message from the buffer as "drop" */
//            deleteMessage(m.getId(), true);
//            nrofdrops++;
//            System.out.println("Dropssssss: " + nrofdrops);
//
//            freeBuffer += m.getSize();
//        }
//
//        return true;
//    }
//    
//    //Menghitung Message Generate
//    @Override
//    	public boolean createNewMessage(Message m) {
//		if (this.endtimeofmsgcreation == 0
//				|| SimClock.getTime() - this.endtimeofmsgcreation >= this.msggenerationinterval) {
//			this.endtimeofmsgcreation = SimClock.getTime();
//			/* added repsproperty to count the 
//			 * number of replications for a new message*/
////			m.addProperty(repsproperty, 1);
//                        nrofgen++;
//                        System.out.println("GENN: " + nrofgen);
//			return super.createNewMessage(m);
//		}
//                
//		return false;
//
//	}
//      @Override
//    public int receiveMessage(Message m, DTNHost from) {
//        nrofrec++;
//        System.out.println("RECC: " + nrofrec);
//        return super.receiveMessage(m, from); //To change body of generated methods, choose Tools | Templates.
//    }
//
//    /**
//     * Tries to send all other messages to all connected hosts ordered by their
//     * delivery probability
//     *
//     * @return The return value of {@link #tryMessagesForConnected(List)}
//     */
//    private Tuple<Message, Connection> tryOtherMessages() {
//        List<Tuple<Message, Connection>> messages
//                = new ArrayList<Tuple<Message, Connection>>();
//
//        Collection<Message> msgCollection = getMessageCollection();
//
//        /* for all connected hosts collect all messages that have a higher
//		   probability of delivery by the other host */
//        for (Connection con : getConnections()) {
//            DTNHost other = con.getOtherNode(getHost());
//            ProphetRouterCL3 othRouter = (ProphetRouterCL3) other.getRouter();
//
//            if (othRouter.isTransferring()) {
//                continue; // skip hosts that are transferring
//            }
//
//            for (Message m : msgCollection) {
//                if (othRouter.hasMessage(m.getId())) {
//                    continue; // skip messages that the other one has
//                }
//                tryAllMessagesToAllConnections();
//                if (othRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
//                    // the other node has higher probability of delivery
//                    messages.add(new Tuple<Message, Connection>(m, con));
//                }
//            }
//        }
//
//        if (messages.size() == 0) {
//            return null;
//        }
//
//        // sort the message-connection tuples
//        Collections.sort(messages, new TupleComparator());
//        return tryMessagesForConnected(messages);	// try to send messages
//    }
//
//    @Override
//    public List<CVandTime> getCLandTime() {
//        return this.clandtime;
//    }
//
//    protected double calculateCL() {
////        ProphetRouterCL3 othRouter = (ProphetRouterCL3) peer.getRouter();
////	int totalhops = msgtotalhops();
////		int totaldrop = this.nrofdrops + this.otherNrofDrops;
//                int totaldrop2 = this.nrofdrops;
////		int totalreps = this.nrofreps + totalhops + this.otherNrofReps;
//                int totalrec = this.nrofrec;
//                int totalgen = this.nrofgen;
////                System.out.println("DROPS CV 1: " + totaldrop);
//                 System.out.println("DROPS CV 2: " + totaldrop2);
//                 System.out.println("REC CV 1: " + totalrec);
//                 System.out.println("GEN CV 2: " + totalgen);
//                 System.out.println("preds : " + preds);
//		// reset 
//		nrofdrops = 0;
//                nrofrec = 0;
//                nrofgen=0;
////		nrofreps = 0;
////		otherNrofDrops = 0;
////		otherNrofReps = 0;
//
//		double ratio;
//		if (totalrec + totalgen != 0) {
//			ratio = (double) totaldrop2 / ((double) totalrec + (double)totalgen);
////			this.ratio = ratio;
//			return (alpha * ratio) + ((1.0 - alpha) * CL);
//		} else {
//			return CL;
//		}
//
//    }
//    /**
//     * Comparator for Message-Connection-Tuples that orders the tuples by their
//     * delivery probability by the host on the other side of the connection
//     * (GRTRMax)
//     */
//    private class TupleComparator implements Comparator<Tuple<Message, Connection>> {
//
//        public int compare(Tuple<Message, Connection> tuple1,
//                Tuple<Message, Connection> tuple2) {
//            // delivery probability of tuple1's message with tuple1's connection
//            double p1 = ((ProphetRouterCL3) tuple1.getValue().
//                    getOtherNode(getHost()).getRouter()).getPredFor(
//                    tuple1.getKey().getTo());
//            // -"- tuple2...
//            double p2 = ((ProphetRouterCL3) tuple2.getValue().
//                    getOtherNode(getHost()).getRouter()).getPredFor(
//                    tuple2.getKey().getTo());
//
//            // bigger probability should come first
//            if (p2 - p1 == 0) {
//                /* equal probabilities -> let queue mode decide */
//                return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
//            } else if (p2 - p1 < 0) {
//                return -1;
//            } else {
//                return 1;
//            }
//        }
//    }
//
//    @Override
//    public RoutingInfo getRoutingInfo() {
//        ageDeliveryPreds();
//        RoutingInfo top = super.getRoutingInfo();
//        RoutingInfo ri = new RoutingInfo(preds.size()
//                + " delivery prediction(s)");
//
//        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
//            DTNHost host = e.getKey();
//            Double value = e.getValue();
//
//            ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
//                    host, value)));
//        }
//
//        top.addMoreInfo(ri);
//        return top;
//    }
//
//    @Override
//    public MessageRouter replicate() {
//        ProphetRouterCL3 r = new ProphetRouterCL3(this);
//        return r;
//    }
//
////    private void decreasingnrofreps() {
////        this.msglimit = (int) Math.ceil(this.msglimit * md);
////
////    }
////
////    private void increasingnrofreps() {
////        this.msglimit = this.msglimit + ai;
////
////    }
//
//    /**
//     * when connection up
//     */
//    public void connectionUp(Connection con) {
//
//    }
//
//    /**
//     * when connection down
//     */
//    public void connectionDown(Connection con) {
//
//    }
//
//}
