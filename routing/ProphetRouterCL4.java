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
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of PRoPHET router as described in
 * <I>Probabilistic routing in intermittently connected networks</I> by Anders
 * Lindgren et al.
 */
public class ProphetRouterCL4 extends ActiveRouter {

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

    private int ndrop = 0; // inisialisasi jumlah drop
    private int nrec = 0; // inisialisasi jumlah receive
    private int ngen = 0; // inisialisasi jumlah Gen
    private Map<String, ACKTTL> receiptBuffer; // buffer that save receipt(ACK purposes)
    private Set<String> messageReadytoDelete;
    
    /**
     * Constructor. Creates a new message router based on the settings in the
     * given Settings object.
     *
     * @param s The settings object
     */
    public ProphetRouterCL4(Settings s) {
        super(s);
        Settings prophetSettings = new Settings(PROPHET_NS);
        secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
        if (prophetSettings.contains(BETA_S)) {
            beta = prophetSettings.getDouble(BETA_S);
        } else {
            beta = DEFAULT_BETA;
        }
        initPreds();
        this.receiptBuffer = new HashMap<>();
        this.messageReadytoDelete = new HashSet<>();
    }

    /**
     * Copyconstructor.
     *
     * @param r The router prototype where setting values are copied from
     */
    protected ProphetRouterCL4(ProphetRouterCL4 r) {
        super(r);
        this.secondsInTimeUnit = r.secondsInTimeUnit;
        this.beta = r.beta;
        this.ndrop = r.ndrop;
        this.ngen = r.ngen;
        this.nrec = r.nrec;
        initPreds(); this.receiptBuffer = new HashMap<>();
        this.messageReadytoDelete = new HashSet<>();
        
    }

    /**
     * Initializes predictability hash
     */
    private void initPreds() {
        this.preds = new HashMap<DTNHost, Double>();
    }

    @Override
    public void changedConnection(Connection con) {
        if (con.isUp()) {
            DTNHost otherHost = con.getOtherNode(getHost());
            updateDeliveryPredFor(otherHost);
            updateTransitivePreds(otherHost);

            Collection<Message> thisMessageList = getMessageCollection();
            ProphetRouterCL4 othRouter = (ProphetRouterCL4) otherHost.getRouter();

            // Exchange receipt buffer
            Map<String, ACKTTL> peerReceiptBuffer = othRouter.getReceiptBuffer();

            for (Map.Entry<String, ACKTTL> entry : peerReceiptBuffer.entrySet()) {
                if (!receiptBuffer.containsKey(entry.getKey())) {
                    receiptBuffer.put(entry.getKey(), entry.getValue());
                }
            }
            for (Message m : thisMessageList) {
                // Delete message that have a receipt
                if (receiptBuffer.containsKey(m.getId())) {
                    messageReadytoDelete.add(m.getId());
                }
            }
            for (String m : messageReadytoDelete) {
                if (isSending(m)) {
                    List<Connection> conList = getConnections();
                    for (Connection conn : conList) {
                        if (conn.getMessage() != null && conn.getMessage().getId() == m) {
                            conn.abortTransfer();;
                            break;
                        }
                    }
                }
                deleteMessage(m, false);
            }
            messageReadytoDelete.clear();
        } else {
            DTNHost otherHost = con.getOtherNode(getHost());
//            updateCongestionValue(otherHost);
//            updateCvFor(otherHost);
//            DTNHost otherHost = con.getOtherNode(getHost());
//            System.out.println("Reps : " + this.nrOfReps);
            System.out.println("Drops awallalalla : " + this.ndrop);
//            double CVnew = calculateCV(con, otherHost);
//
//            CVTime cvValue = new CVTime(CVnew, SimClock.getTime());
//            cvList.add(cvValue);
//            this.CV = CVnew;
//            System.out.println("CV : " + this.CV);

            messageReadytoDelete.clear();
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

    /**
     * Updates transitive (A->B->C) delivery predictions.      <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
	 * </CODE>
     *
     * @param host The B host who we just met
     */
    private void updateTransitivePreds(DTNHost host) {
        MessageRouter otherRouter = host.getRouter();
        assert otherRouter instanceof ProphetRouterCL4 : "PRoPHET only works "
                + " with other routers of same type";

        double pForHost = getPredFor(host); // P(a,b)
        Map<DTNHost, Double> othersPreds
                = ((ProphetRouterCL4) otherRouter).getDeliveryPreds();

        for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
            if (e.getKey() == getHost()) {
                continue; // don't add yourself
            }

            double pOld = getPredFor(e.getKey()); // P(a,c)_old
            double pNew = pOld + (1 - pOld) * pForHost * e.getValue() * beta;
            preds.put(e.getKey(), pNew);
        }
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

    @Override
    public void update() {
        super.update();
//        double newCL = calculateCL();
        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring 
        }

        // try messages that could be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        tryOtherMessages();
    }

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
            ProphetRouterCL4 othRouter = (ProphetRouterCL4) other.getRouter();

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

    /**
     * Comparator for Message-Connection-Tuples that orders the tuples by their
     * delivery probability by the host on the other side of the connection
     * (GRTRMax)
     */
    private class TupleComparator implements Comparator<Tuple<Message, Connection>> {

        public int compare(Tuple<Message, Connection> tuple1,
                Tuple<Message, Connection> tuple2) {
            // delivery probability of tuple1's message with tuple1's connection
            double p1 = ((ProphetRouterCL4) tuple1.getValue().
                    getOtherNode(getHost()).getRouter()).getPredFor(
                    tuple1.getKey().getTo());
            // -"- tuple2...
            double p2 = ((ProphetRouterCL4) tuple2.getValue().
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

//    @Override
//    public int receiveMessage(Message m, DTNHost from) {
//        nrec++;
//        System.out.println("Recccc : " + nrec);
//        return super.receiveMessage(m, from); //To change body of generated methods, choose Tools | Templates.
//    }
//
//    @Override
//    public boolean createNewMessage(Message m) {
//        ngen++;
//        System.out.println("Gen : " + ngen);
//        return super.createNewMessage(m); //To change body of generated methods, choose Tools | Templates.
//    }

    
    
    @Override
    public MessageRouter replicate() {
        ProphetRouterCL4 r = new ProphetRouterCL4(this);
        return r;
    }
    
    public Map<String, ACKTTL> getReceiptBuffer() {
        return receiptBuffer;
    }
    
//    protected double calculateCL() {
////        ProphetRouterCL3 othRouter = (ProphetRouterCL3) peer.getRouter();
//        
//        int drops = this.ndrop;
//        int rec = this.nrec;
//        int gen = this.ngen;
//        System.out.println("drops : " + drops);
//        System.out.println("rec : " + rec);
//        System.out.println("gen : " + gen);
//// 
////            nrofdrops = 0;
////            nrofrec = 0;
////            nrofgen = 0;    
////        
////        if (nrofrec + nrofgen != 0) {
////            System.out.println("drop: " + nrofdrops);
////            System.out.println("rec: " + nrofrec);
////            System.out.println("gen: " + nrofgen);
//////            System.out.println("drops1 : " + drops);
//////            System.out.println("rec1 : " + rec);
//////            System.out.println("gen1 : " + gen);
////            return alpha * drops / (rec + gen) + (1 - alpha) * CL;
//////                return alpha * nrOfDrops / (nrOfRec + nrOfGen) + (1 - alpha) * CL;
////        } else {
////            return CL;
////        }
//    return drops;
//    }
    
     @Override
    protected boolean makeRoomForMessage(int size) {
        if (size > this.getBufferSize()) {
            return false; // message too big for the buffer
        }

        int freeBuffer = this.getFreeBufferSize();
        /* delete messages from the buffer until there's enough space */
        while (freeBuffer < size) {
            Message m = getOldestMessage(true); // don't remove msgs being sent

            if (m == null) {
                return false; // couldn't remove any more messages
            }

            /* delete message from the buffer as "drop" */
            deleteMessage(m.getId(), true);
            ndrop++;
            System.out.println("Ndrop = " + ndrop);
            freeBuffer += m.getSize();
        }

        return true;
    }

}
