/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.Tuple;
import report.MessageStatsReport;

/**
 * Implementation of PRoPHET router as described in
 * <I>Probabilistic routing in intermittently connected networks</I> by Anders
 * Lindgren et al.
 */
public class ProphetActiveCL extends ActiveRouter  {

    /**
     * delivery predictability initialization constant
     */
    public static final double P_INIT = 0.75;
    /**
     * delivery predictability transitivity scaling constant default value
     */
    public static final double DEFAULT_BETA = 0.25;
    /**
     * delivery predictability aging constant
     */
    public static final double GAMMA = 0.98;

    /**
     * Prophet router's setting namespace ({@value})
     */
    public static final String PROPHET_NS = "ProphetRouter";
    /**
     * Number of seconds in time unit -setting id ({@value}). How many seconds
     * one time unit is when calculating aging of delivery predictions. Should
     * be tweaked for the scenario.
     */
    public static final String SECONDS_IN_UNIT_S = "secondsInTimeUnit";

    /**
     * Transitivity scaling constant (beta) -setting id ({@value}). Default
     * value for setting is {@link #DEFAULT_BETA}.
     */
    public static final String BETA_S = "beta";

    /**
     * the value of nrof seconds in time unit -setting
     */
    private int secondsInTimeUnit;
    /**
     * value of beta setting
     */
    private double beta;

    /**
     * delivery predictabilities
     */
    private Map<DTNHost, Double> preds;

    /**
     * last delivery predictability update (sim)time
     */
    private double lastAgeUpdate;

    //////TEST
    public int nrOfDrops = 0; // inisialisasi jumlah drop
    public int nrOfRec = 0; // inisialisasi jumlah receive
    public int nrOfGen = 0; // inisialisasi jumlah Gen
    protected double CL = 0.0;// inisialisasi CL
//    private int numberOfNodes; // jumlah node(kekny)
    private double eta; // threshold value for CL
    public static final String ALPHA_CL = "alphaCL";
    public static final double DEFAULT_ALPHA = 0.5;
    public double ALPHA = 0.5;
    double CLold=0.0;
//    private int buffer_occu;
//    private int buffer_cap;
    private Map<DTNHost, Double> cln;
    public List<Double> clList = new ArrayList<>();
    public List<DTNHost> nodeList = new ArrayList<>();

    public double dps;

//    private List<Integer> counters = new ArrayList<>(num_nodes); 
// buat array kosong dengan panjang num_nodes ^^
    /**
     * Constructor. Creates a new message router based on the settings in the
     * given Settings object.
     *
     * @param s The settings object
     */
    public ProphetActiveCL(Settings s) {
        super(s);
        Settings prophetSettings = new Settings(PROPHET_NS);
        secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
        if (prophetSettings.contains(BETA_S)) {
            beta = prophetSettings.getDouble(BETA_S);
        } else {
            beta = DEFAULT_BETA;
        }

        if (prophetSettings.contains(ALPHA_CL)) {
            ALPHA = prophetSettings.getDouble(ALPHA_CL);
        } else {
            ALPHA = DEFAULT_ALPHA;
        }

        initPreds();

    }

    /**
     * Copyconstructor.
     *
     * @param r The router prototype where setting values are copied from
     */
    protected ProphetActiveCL(ProphetActiveCL r) {
        super(r);
        this.secondsInTimeUnit = r.secondsInTimeUnit;
        this.beta = r.beta;
        initPreds();
        this.nrOfDrops = r.nrOfDrops;
        this.nrOfGen = r.nrOfGen;
        this.nrOfRec = r.nrOfRec;
        this.ALPHA = r.ALPHA;
        this.eta = r.eta;
        this.CL = r.CL;
        
//                ////TEST
//                this.numberOfNodes = numberOfNodes;
    }

    /**
     * Initializes predictability hash
     */
    private void initPreds() {
        this.preds = new HashMap<DTNHost, Double>();
    }

    private void initCln() {
        this.cln = new HashMap<DTNHost, Double>();
    }

    //MENUKAR KONEKSI NAH INI NTAR DIPAKE PAS NUKER INFO2 CLCL NYA
    @Override
    public void changedConnection(Connection con) {
        if (con.isUp()) {
            DTNHost otherHost = con.getOtherNode(getHost());
            updateDeliveryPredFor(otherHost);
            updateTransitivePreds(otherHost);
            
//            DTNHost otherHost = con.getOtherNode(getHost());
//            List<Connection> conn = host.getConnections();
//            for (Connection c : conn) {
                calculateCL(otherHost);
                System.out.println("CLcon :");
//            }
        }
    }

    /**
     * Updates delivery predictions for a host.
     * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
     *
     * @param host The host we just met
     */
    private void updateDeliveryPredFor(DTNHost host) {
        double oldValue = getPredFor(host);
        double newValue = oldValue + (1 - oldValue) * P_INIT;
        preds.put(host, newValue);

    }

//    private void updateDeliveryClnFor(DTNHost host){
//        double oldCln = calculateCL(host);
//        double newCln = ALPHA * dps + (1 - ALPHA) * oldCln;
//        cln.put(host, newCln);
//    }
    /**
     * Returns the current prediction (P) value for a host or 0 if entry for the
     * host doesn't exist.
     *
     * @param host The host to look the P for
     * @return the current P value
     */
    public double getPredFor(DTNHost host) {
        ageDeliveryPreds(); // make sure preds are updated before getting
        if (preds.containsKey(host)) {
            return preds.get(host);
        } else {
            return 0;
        }
    }

    public double getClFor(DTNHost host) {
        if (cln.containsKey(host)) {
            return cln.get(host);
        } else {
            return 0;
        }
    }

//    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
//		if (isWarmupID(m.getId())) {
//			return;
//		}
//		
//		if (dropped) {
//			this.nrofDropped++;
//		}
//		else {
//			this.nrofRemoved++;
//		}
//		
//		this.msgBufferTime.add(getSimTime() - m.getReceiveTime());
//	}
    public void messageDeleted(MessageStatsReport mr, Message m, DTNHost where) {
        mr.messageDeleted(m, where, deleteDelivered);
        mr.getNrofDropped();

    }

    /**
     * Updates transitive (A->B->C) delivery predictions.      <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
     * </CODE>
     *
     * @param host The B host who we just met
     */
    private void updateTransitivePreds(DTNHost host) {
        MessageRouter otherRouter = host.getRouter();
        assert otherRouter instanceof ProphetActiveCL : "PRoPHET only works "
                + " with other routers of same type";

        double pForHost = getPredFor(host); // P(a,b)
        Map<DTNHost, Double> othersPreds
                = ((ProphetActiveCL) otherRouter).getDeliveryPreds();

        for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
            if (e.getKey() == getHost()) {
                continue; // don't add yourself
            }

            double pOld = getPredFor(e.getKey()); // P(a,c)_old
            double pNew = pOld + (1 - pOld) * pForHost * e.getValue() * beta;
            preds.put(e.getKey(), pNew);
        }

//         Map<DTNHost, Double> othersCln
//                = ((ProphetActiveCL) otherRouter).getDeliveryCln();
//         
//         for (DTNHost h : othersCln.keySet()) {
//             System.out.println("test"+h);
//}
//        for (Map.Entry<DTNHost, Double> e : othersCln.entrySet()) {
//            if (e.getKey() == getHost()) {
//               
//                continue; // don't add yourself         
//            }
//            double newCL = calculateCL(host);
//            System.out.println("New CL : " + newCL);
//            cln.put(e.getKey(), newCL);
//}
    }

    /**
     * Ages all entries in the delivery predictions.
     * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of time
     * units that have elapsed since the last time the metric was aged.
     *
     * @see #SECONDS_IN_UNIT_S
     */
    private void ageDeliveryPreds() {
        double timeDiff = (SimClock.getTime() - this.lastAgeUpdate)
                / secondsInTimeUnit;

        if (timeDiff == 0) {
            return;
        }

        double mult = Math.pow(GAMMA, timeDiff);
        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
            e.setValue(e.getValue() * mult);
        }

        this.lastAgeUpdate = SimClock.getTime();
    }

    /**
     * Returns a map of this router's delivery predictions
     *
     * @return a map of this router's delivery predictions
     */
    private Map<DTNHost, Double> getDeliveryPreds() {
        ageDeliveryPreds(); // make sure the aging is done
        return this.preds;
    }

    private Map<DTNHost, Double> getDeliveryCln() {
        return this.cln;
    }

    @Override
    public void update() {
        super.update();
        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring 
        }

        // try messages that could be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        tryOtherMessages();
    }

    //menghitung drops ////TEST
    @Override
    protected boolean makeRoomForMessage(int size) {
        if (size > this.getBufferSize()) {
            return false; // pesan terlalu besar utk buffer
        }

        int freeBuffer = this.getFreeBufferSize();
//        hapus pesan dari buffer sampe spacenya cukup
        while (freeBuffer < size) {
            Message m = getOldestMessage(true);
            if (m == null) {
                return false; //gak bisa hapus pesan lagi
            }
            //hapus pesan dari buffer sbg drop
            deleteMessage(m.getId(), true);
//            this.nrOfDrops++;

            freeBuffer += m.getSize();
        }

        return true;
    }

    //Menghitung hpsf
    private int messagestotalHops() {
        Collection<Message> msg = getMessageCollection();
        int totalHops = 0;
        if (!msg.isEmpty()) {
            for (Message m : msg) {
                if (!(m.getHopCount() == 0)) {
                    totalHops += (m.getHopCount() - 1);
                }
            }
        }
        return totalHops;
    }

    // mengembalikan nilai CL
    private void calculateCL(DTNHost peer) {

        ProphetActiveCL othRouter = (ProphetActiveCL) peer.getRouter();
//        
        int drops = othRouter.nrOfDrops;
        int rec = othRouter.nrOfRec;
        int gen = othRouter.nrOfGen;
        List<Connection> conn = host.getConnections();
        //added


        CLold = 0;
        dps = ((double) drops / ((double) rec + (double) gen)); // itungan CL awal
//        dps2=
        CL = ALPHA * dps + (1 - ALPHA) * CLold; // itungan yg nomor (4)

//        //INI ITUNGAN CL COBA RUMUS ROUTING PROSES NOMOR (6) YA! 
//        buffer_occu =  this.getBufferSize() - this.getFreeBufferSize();
//        if (nrOfDrops <= (nrOfRec + nrOfGen)){
//            CL = Math.sin(((Math.PI/2*(this.getBufferSize())) * buffer_occu));
//        }else if(nrOfDrops >= (nrOfRec + nrOfGen)){
//            boolean name = CL > 1;  //  ni gatau nie knp eror
//        }
//        cln.put(peer, CL);   
//        System.out.println("ndrop : " + nrOfDrops);
//        System.out.println("nrec : " + nrOfRec);
//        System.out.println("ngen : " + nrOfGen);
        System.out.println("drop : " + drops);

//        for (Connection c : conn) {
//            DTNHost other = (DTNHost) c.getOtherNode(host);
            System.out.println("nodee : " + othRouter.toString());
//            System.out.println("nodee2 : " + othRouter.getMessageeCountGen());
//        }
//        for(ProphetActiveCL nodee : ProphetActiveCL){
//            
//        }

        System.out.println("rec : " + rec);
        System.out.println("gen : " + gen);
        System.out.println("nilai CL biasa : " + CL);
        System.out.println("DPS : " + dps);
        System.out.println("Node: " + preds);
        System.out.println("CL pake Map: " + cln);
 

        clList.add(CL);
        System.out.println("cllisttt :" + clList);      
        System.out.println("------------");
    }

//         return rasio; //MASIH NGASAL NI BIAR KAGA EROR 
    private void printCongestionLevelList() {
        System.out.println("Congestion Levels:");
        for (Double level : clList) {
            System.out.println(level);
        }
    }

//    @Override
//    public void sendMessage(String id, DTNHost to) {
//        
////        calculateCL(to);
////        printCongestionLevelList();
//        sendMessage(id, to); //To change body of generated methods, choose Tools | Templates.
////         ProphetActiveCL router = (ProphetActiveCL) to.getRouter();
////         List<Double> congestionLevels = router.getCongestionLevel();
////        System.out.println("CongestionLevel : ");
////        for (Double level : congestionLevels ) {
////            System.out.println(level);
//    }
//    //Menghitung node generate////TEST
//    public int countGenerateNodes(){
//        int generatedNodes = numberOfNodes * (numberOfNodes -1);
//        return generatedNodes;
//    }
//    //Menghitung node receive
//    public int countReceiveNodes(){
//        int receivedNodes = numberOfNodes * (numberOfNodes -1);
//        return receivedNodes;
//    }
    /**
     * Tries to send all other messages to all connected hosts ordered by their
     * delivery probability
     *
     * @return The return value of {@link #tryMessagesForConnected(List)}
     */
    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages
                = new ArrayList<Tuple<Message, Connection>>();

        Collection<Message> msgCollection = getMessageCollection();

        /* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());
            ProphetActiveCL othRouter = (ProphetActiveCL) other.getRouter();

            if (othRouter.isTransferring()) {
                continue; // skip hosts that are transferring
            }

            for (Message m : msgCollection) {
                if (othRouter.hasMessage(m.getId())) {
                    continue; // skip messages that the other one has
                }
                tryAllMessagesToAllConnections();
                if (othRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
                    // the other node has higher probability of delivery
                    messages.add(new Tuple<Message, Connection>(m, con));
                }
            }
        }

        if (messages.size() == 0) {
            return null;
        }

        // sort the message-connection tuples
        Collections.sort(messages, new TupleComparator());
        return tryMessagesForConnected(messages);	// try to send messages
    }

    protected int startTransfer(Message m, Connection con) {
        int retVal;

        if (!con.isReadyForTransfer()) {
            return TRY_LATER_BUSY;
        }

        retVal = con.startTransfer(getHost(), m);
        if (retVal == RCV_OK) { // started transfer
            addToSendingConnections(con);

//            DTNHost otherHost = con.getOtherNode(getHost());
//            List<Connection> conn = host.getConnections();
//            for (Connection c : conn) {
//                calculateCL(otherHost);
//                System.out.println("CLcon :");
//            }

            printCongestionLevelList();
//            double newCL = calculateCL(otherHost);
//            CLTime clValue = new CLTime(newCL, SimClock.getTime());
//            clList.add(clValue);
//            System.out.println("New CL : " + newCL);
//            System.out.println("clValue : " + clValue);
//            System.out.println("clList : " + clList);

//            cln.put(otherHost, newCL);
//            cln.put(otherHost, newCL);
//            ///////////////klo salah apus aj//////////////
//              MessageRouter otherRouter = otherHost.getRouter();
//       
//        Map<DTNHost, Double> othersCln = 
//			((ProphetActiveCL)otherRouter).getDeliveryPreds();
//        for (Map.Entry<DTNHost,Double> e : othersCln.entrySet()){
//            if(e.getKey() == getHost()){
//                continue;
//            }  
//            
//            
//            cln.put(e.getKey(), newCL);
//        }
            ////////////////klo salah apus aj///////////////////
//            cln.put(otherHost,newCL );
//            if (newCL <= 0 || newCL <= eta) {
//                super.startTransfer(m, con);
//            } else if (newCL >= eta && newCL <= 1) {
//                m.getSize();
//                super.startTransfer(m, con);
//            } else if (newCL > 1) {
//                m.getSize(0);
//                super.startTransfer(m, con);
//            }
        } else if (deleteDelivered && retVal == DENIED_OLD
                && m.getTo() == con.getOtherNode(this.getHost())) {
            /* final recipient has already received the msg -> delete it */
            this.deleteMessage(m.getId(), false);
        }

        return retVal;
    }

    public List<Double> getCongestionLevel() {
        return clList;
    }

    public List<DTNHost> getNodeList() {
        return nodeList;
    }

    

    /**
     * Comparator for Message-Connection-Tuples that orders the tuples by their
     * delivery probability by the host on the other side of the connection
     * (GRTRMax)
     */
    private class TupleComparator implements Comparator<Tuple<Message, Connection>> {

        public int compare(Tuple<Message, Connection> tuple1,
                Tuple<Message, Connection> tuple2) {
            // delivery probability of tuple1's message with tuple1's connection
            double p1 = ((ProphetActiveCL) tuple1.getValue().
                    getOtherNode(getHost()).getRouter()).getPredFor(
                    tuple1.getKey().getTo());
            // -"- tuple2...
            double p2 = ((ProphetActiveCL) tuple2.getValue().
                    getOtherNode(getHost()).getRouter()).getPredFor(
                    tuple2.getKey().getTo());

            // bigger probability should come first
            if (p2 - p1 == 0) {
                /* equal probabilities -> let queue mode decide */
                return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
            } else if (p2 - p1 < 0) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message m = super.messageTransferred(id, from);
        if(m!= null && m.getTo() == this.getHost()){
            nrOfDrops++;
        }
        return m; //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public RoutingInfo getRoutingInfo() {
        ageDeliveryPreds();
        RoutingInfo top = super.getRoutingInfo();
        RoutingInfo ri = new RoutingInfo(preds.size()
                + " delivery prediction(s)");

        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
            DTNHost host = e.getKey();
            Double value = e.getValue();

            ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
                    host, value)));
        }

        top.addMoreInfo(ri);
        return top;
    }

    
    
    
    
    
    @Override
    public MessageRouter replicate() {
        ProphetActiveCL r = new ProphetActiveCL(this);
        return r;
    }

//    @Override
//    public Message messageTransferred(String id, DTNHost from) {
//        Message m = super.messageTransferred(id, from);
//        if(this.createNewMessage(m)){
//            nrOfGen++;
//        }else if (this.receiveMessage(m, from) == 0){
//            nrOfRec++;
//        }
//        return super.messageTransferred(id, from); //To change body of generated methods, choose Tools | Templates.
//    }
    //Menghitung Message Receive
    @Override
    public int receiveMessage(Message m, DTNHost from) {
        nrOfRec++;

//        System.out.println("Jumlah message yang diterima : " + nrOfRec + ",");
        return super.receiveMessage(m, from); //To change body of generated methods, choose Tools | Templates.
    }

//    public int getMessageeCountRec(){
//        return this.nrOfRec;
//    }
    //Menghitung Message Generate
    @Override
    public boolean createNewMessage(Message m) {
        nrOfGen++;
//        System.out.println("Jumlah message yang dibuat : " + nrOfGen + ",");
        return super.createNewMessage(m); //To change body of generated methods, choose Tools | Templates.
    }

//     public int getMessageeCountGen(){
//        return this.nrOfGen;
//    }
}
