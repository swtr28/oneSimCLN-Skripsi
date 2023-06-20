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

/**
 * Implementation of PRoPHET router as described in
 * <I>Probabilistic routing in intermittently connected networks</I> by Anders
 * Lindgren et al.
 */
public class ProphetRouterWithCvOnly extends ActiveRouter implements CVDetectionEngine {

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
     * Alpha for CV's learning rate - setting id (@value)
     */
    public static final String ALPHA_CV_S = "alphaCV";
    /**
     * minimum time for update new state (window) - setting id (@value)
     */
    public static final String STATE_UPDATE_INTERVAL_S = "stateInterval";

    /**
     * default value for state interval update
     */
    public static final double DEFAULT_STATE_UPDATE_INTERVAL = 300;
    /**
     * identifier for the initial number of copies setting ({@value})
     */
    public static final String NROF_COPIES = "nrofCopies";

    /**
     * Prophet router's setting namespace ({@value})
     */
    public static final String PROPHET_NS = "ProphetRouterWithCvOnly";
    /**
     * Message property key
     */
    public static final String MSG_COUNT_PROPERTY = PROPHET_NS + "." + "copies";
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
     * default value for alpha
     */
    public static final double DEFAULT_ALPHA_CV = 0.9;
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

    /**
     * dummy variable to count number of reps
     */
    private int nrofreps = 0;
    /**
     * dummy variable to count number of drops
     */
    private int nrofdrops = 0;
    /**
     * dummy variable to count reps number of the other hosts
     */
    private int otherNrofReps = 0;
    /**
     * dummy variable to count number of drops number of the other hosts
     */
    private int otherNrofDrops = 0;
    /**
     * message property to record its number of copies
     */
    public static final String repsproperty = "nrofcopiesofreps";
    /**
     * to record the last time of message creation
     */
    private double endtimeofmsgcreation = 0;

    /**
     * message generation interval in seconds
     */
    protected double msggenerationinterval = 600;
    /**
     * buffer that save receipt
     */
    protected Map<String, ACKTTL> receiptBuffer;
    /**
     * ratio of drops and reps
     */
    private double ratio = 0;
    /**
     * value of cv alpha setting
     */
    private double alpha;
    /**
     * congestion value - ratio of drops and reps, counted with EWMA equation
     */
    private double CV = 0;
    /**
     * dummy variable to set the interval to count the new CV to detect the new
     * state
     */
    private double LastUpdateTimeofState = 0;
    /**
     * value of stateUpdateInterval setting
     */
    private double stateUpdateInterval;
    /**
     * needed for CV report
     */
    private List<CVandTime> cvandtime;
    protected int initialNrofCopies;

    /**
     * Constructor. Creates a new message router based on the settings in the
     * given Settings object.
     *
     * @param s The settings object
     */
    public ProphetRouterWithCvOnly(Settings s) {
        super(s);
        Settings prophetSettings = new Settings(PROPHET_NS);
        secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
        initialNrofCopies = prophetSettings.getInt(NROF_COPIES);
        if (prophetSettings.contains(BETA_S)) {
            beta = prophetSettings.getDouble(BETA_S);
        } else {
            beta = DEFAULT_BETA;
        }
        if (prophetSettings.contains(ALPHA_CV_S)) {
            alpha = prophetSettings.getDouble(ALPHA_CV_S);
        } else {
            alpha = DEFAULT_ALPHA_CV;
        }
        if (prophetSettings.contains(STATE_UPDATE_INTERVAL_S)) {
            stateUpdateInterval = prophetSettings.getDouble(STATE_UPDATE_INTERVAL_S);
        } else {
            stateUpdateInterval = DEFAULT_STATE_UPDATE_INTERVAL;
        }

        initPreds();
    }

    /**
     * Copyconstructor.
     *
     * @param r The router prototype where setting values are copied from
     */
    protected ProphetRouterWithCvOnly(ProphetRouterWithCvOnly r) {
        super(r);
        this.secondsInTimeUnit = r.secondsInTimeUnit;
        this.beta = r.beta;
        this.alpha = r.alpha;
        this.initialNrofCopies = r.initialNrofCopies;
        this.stateUpdateInterval = r.stateUpdateInterval;
        cvtimelist();
        initPreds();
        receiptbuffer();

    }

    /**
     * Initializes predictability hash
     */
    private void initPreds() {
        this.preds = new HashMap<DTNHost, Double>();
    }

    @Override
    /* needed for CV report */
    public List<CVandTime> getCVandTime() {
        return this.cvandtime;
    }

    @Override
    public void changedConnection(Connection con) {
        if (con.isUp()) {
            connectionUp(con);
            DTNHost otherHost = con.getOtherNode(getHost());
            updateDeliveryPredFor(otherHost);
            updateTransitivePreds(otherHost);
        } else {
            connectionDown(con);
            DTNHost otherHost = con.getOtherNode(getHost());
            ProphetRouterWithCvOnly peerRouter = (ProphetRouterWithCvOnly) otherHost.getRouter();
            /* record the peer's nrofdrops & nrofreps 
			 * as otherNrofDrops & otherNrofReps  */
            otherNrofDrops += peerRouter.getNrofDrops();
            otherNrofReps += peerRouter.getNrofReps();

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

    /* exchange mesage's information of the reps number **/
    protected void exchangemsginformation() {
        Collection<Message> msgCollection = getMessageCollection();
        for (Connection con : getConnections()) {
            DTNHost peer = con.getOtherNode(getHost());
            ProphetRouterWithCvOnly other = (ProphetRouterWithCvOnly) peer.getRouter();
            if (other.isTransferring()) {
                continue; // skip hosts that are transferring
            }
            for (Message m : msgCollection) {
                if (other.hasMessage(m.getId())) {
                    Message temp = other.getMessage(m.getId());
                    /* take the max reps */
                    if ((Integer) m.getProperty(repsproperty) < (Integer) temp.getProperty(repsproperty)) {
                        m.updateProperty(repsproperty, temp.getProperty(repsproperty));
                    }
                }

            }
        }
    }

    @Override

    public boolean createNewMessage(Message m) {
        if (this.endtimeofmsgcreation == 0
                || SimClock.getTime() - this.endtimeofmsgcreation >= this.msggenerationinterval) {
            this.endtimeofmsgcreation = SimClock.getTime();
            /* added repsproperty to count the 
			 * number of replications for a new message*/
            m.addProperty(repsproperty, 1);
            m.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
            return super.createNewMessage(m);
        }

        return false;

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
        assert otherRouter instanceof ProphetRouterWithCvOnly : "PRoPHET only works "
                + " with other routers of same type";

        double pForHost = getPredFor(host); // P(a,b)
        Map<DTNHost, Double> othersPreds
                = ((ProphetRouterWithCvOnly) otherRouter).getDeliveryPreds();

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

        /* create a list of SAWMessages that have copies left to distribute */
        @SuppressWarnings(value = "unchecked")
        List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());

        if (copiesLeft.size() > 0) {
            /* try to send those messages */
            this.tryMessagesToConnections(copiesLeft, getConnections());
        }

        if ((SimClock.getTime() - LastUpdateTimeofState) >= stateUpdateInterval) {

            double newCV = countcv();
            CVandTime nilaicv = new CVandTime(newCV, SimClock.getTime());
            cvandtime.add(nilaicv);
            this.CV = newCV;
            LastUpdateTimeofState = SimClock.getTime();

        }
        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring 
        }

        // try messages that could be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        tryOtherMessages();
    }

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
            nrofdrops++;
            freeBuffer += m.getSize();
        }
        List<Message> messages = new ArrayList<Message>(this.getMessageCollection());
        for (Message m : messages) {
            if (freeBuffer < size) {
                deleteMessage(m.getId(), true);
                nrofdrops++;
                freeBuffer += m.getSize();
            } else {
                return true;
            }
        }
        if (freeBuffer < size) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message aCopy = super.messageTransferred(id, from);
        Integer msgprop = ((Integer) aCopy.getProperty(repsproperty)) + 1;

        aCopy.updateProperty(repsproperty, msgprop);

        // replications are counted by successful incoming replications.
        // +1 for 1 rep./
        nrofreps++;
        // ack
        if (isFinalDest(aCopy, this.getHost()) && !receiptBuffer.containsKey(aCopy.getId())) {
            ACKTTL ack = new ACKTTL(SimClock.getTime(), aCopy.getTtl());
            receiptBuffer.put(aCopy.getId(), ack);
        }

        return aCopy;
    }

    /**
     * count message hops
     */
    protected int msgtotalhops() {
        Collection<Message> msg = getMessageCollection();
        int totalhops = 0;
        if (!msg.isEmpty()) {
            for (Message m : msg) {
                if (m.getHopCount() != 0) {
                    totalhops += (m.getHopCount() - 1);
                }
            }
        }
        return totalhops;
    }

    /**
     * calculate the CV
     */
    protected double countcv() {
        int totalhops = msgtotalhops();
        int totaldrop = this.nrofdrops + this.otherNrofDrops;
        int totalreps = this.nrofreps + totalhops + this.otherNrofReps;

        // reset 
        nrofdrops = 0;
        nrofreps = 0;
        otherNrofDrops = 0;
        otherNrofReps = 0;

        double ratio;
        if (totalreps != 0) {
            ratio = (double) totaldrop / (double) totalreps;
            this.ratio = ratio;
            return (alpha * ratio) + ((1.0 - alpha) * CV);
        } else {
            return CV;
        }

    }

    protected void cvtimelist() {
        this.cvandtime = new ArrayList<CVandTime>();
    }

    /**
     * check if this host is the final dest
     */
    protected boolean isFinalDest(Message m, DTNHost thisHost) {
        return m.getTo().equals(thisHost);
    }

    /**
     * when connection up
     */
    public void connectionUp(Connection con) {

    }

    /**
     * when connection down
     */
    public void connectionDown(Connection con) {

    }

    public int getNrofReps() {
        return this.nrofreps;
    }

    public int getNrofDrops() {
        return this.nrofdrops;
    }

    protected void receiptbuffer() {
        this.receiptBuffer = new HashMap<>();
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
            ProphetRouterWithCvOnly othRouter = (ProphetRouterWithCvOnly) other.getRouter();

            if (othRouter.isTransferring()) {
                continue; // skip hosts that are transferring
            }

            for (Message m : msgCollection) {
                if (othRouter.hasMessage(m.getId())) {
                    continue; // skip messages that the other one has
                }
                tryAllMessagesToAllConnections();
                if (othRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
//                    Integer nrofCopies = (Integer) othRouter.getMessage(m.getId()).getProperty(MSG_COUNT_PROPERTY);
                    Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
                    MessageRouter mr = othRouter.getHost().getRouter();
                    ActiveRouter ar = (ActiveRouter) mr;
                    CVDetectionEngine cvde = (CVDetectionEngine) ar;
                    List<CVandTime> cvlist = cvde.getCVandTime();
                    if (cvlist.size() != 0) {
                        for (CVandTime cv : cvlist) {
                            if (cv.CV > 0 && cv.CV <= 0.7) {
                                nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
                            } else if (cv.CV > 0.7 && cv.CV <= 1) {
                                nrofCopies = 1;
                            } else if (cv.CV > 1) {
                                continue;
                            }System.out.println("nrOfCopies :" + nrofCopies);
                            m.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
                        }
                    }
                    
                    messages.add(new Tuple<Message, Connection>(m, con));

                } else {
                    continue;
                }

            }
        }

        if (messages.size()
                == 0) {
            return null;
        }

        // sort the message-connection tuples
        Collections.sort(messages,
                new TupleComparator());
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
            double p1 = ((ProphetRouterWithCvOnly) tuple1.getValue().
                    getOtherNode(getHost()).getRouter()).getPredFor(
                    tuple1.getKey().getTo());
            // -"- tuple2...
            double p2 = ((ProphetRouterWithCvOnly) tuple2.getValue().
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

    /**
     * Called just before a transfer is finalized (by
     * {@link ActiveRouter#update()}). Reduces the number of copies we have left
     * for a message. In binary Spray and Wait, sending host is left with
     * floor(n/2) copies, but in standard mode, nrof copies left is reduced by
     * one.
     */
    @Override
    protected void transferDone(Connection con) {
        String msgId = con.getMessage().getId();
        /* get this router's copy of the message */
        Message msg = getMessage(msgId);
        if (msg == null) {
            return;
        }
        Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
        if (nrofCopies == null) {
            nrofCopies = 1;
        }
        DTNHost other = con.getOtherNode(getHost());
        ProphetRouterWithCvOnly othRouter = (ProphetRouterWithCvOnly) other.getRouter();
        nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
        MessageRouter mr = othRouter;
        ActiveRouter ar = (ActiveRouter) mr;
        CVDetectionEngine cvde = (CVDetectionEngine) ar;
        List<CVandTime> cvlist = cvde.getCVandTime();
        
        System.out.println("nrOfCopies trf done 1:" + nrofCopies);

        if (cvlist.size() != 0) {
            for (CVandTime cv : cvlist) {
                if (cv.CV > 0 && cv.CV <= 0.7) {
                    nrofCopies /= 2;
                } else if (cv.CV > 0.7 && cv.CV <= 1) {
                    nrofCopies--;
                } else if (cv.CV > 1) {
                    continue;
                }
            }

        }

        nrofCopies = Math.max(nrofCopies, 1);
        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
        System.out.println("nrOfCopies trf done 2:" + nrofCopies);

    }

    protected List<Message> getMessagesWithCopiesLeft() {
        List<Message> list = new ArrayList<Message>();

        for (Message m : getMessageCollection()) {
            Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
            assert nrofCopies != null : "SnW message " + m + " didn't have "
                    + "nrof copies property!";
            if (nrofCopies > 1) {
                list.add(m);
            }
            System.out.println("nrOfCopies left :" + nrofCopies);
        }

        return list;
    }

    @Override
    public MessageRouter replicate() {
        ProphetRouterWithCvOnly r = new ProphetRouterWithCvOnly(this);
        return r;
    }
}
