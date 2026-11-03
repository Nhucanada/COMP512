package comp512st.paxos;

// Access to the GCL layer
import comp512.gcl.*;

import comp512.utils.*;

// Any other imports that you may need.
import java.io.*;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.net.UnknownHostException;




// ANY OTHER classes, etc., that you add must be private to this package and not visible to the application layer.

// message wrapper to ensure paxos protocol //TODO: make private.
class PaxosMessage implements Serializable{

	private static final long serialVersionID = 1L;

	// todo: change to enum??
	String type;    //"PROPOSE", "PROMISE", "ACCEPT", "ACCEPTED"

	int instance;
	int ballotNum;
	String sender;
	Object value;
	int acceptedBal;

	public PaxosMessage(String type, int instance, int ballotNumber, String sender, Object value, int acceptedBallot) {
		this.type = type;
		this.instance = instance;
		this.ballotNum = ballotNumber;
		this.sender = sender;
		this.value = value;
		this.acceptedBal = acceptedBallot;
	}
}

	// helper class to track state and responses for an active ballot attempt
	class BallotContext {
		int ballotNum;
		CountDownLatch promiseLatch;
		Set<String> promisedProcs = Collections.synchronizedSet(new HashSet<>());
		int highestOtherAcceptedBallot = -1;
		Object highestOtherAcceptedValue = null;

		CountDownLatch acceptedLatch;
		Set<String> acceptedProcs = Collections.synchronizedSet(new HashSet<>());

		public BallotContext(int ballotNum, int totalProcesses) {
			this.ballotNum = ballotNum;
			int majority = (totalProcesses / 2) + 1;
			this.promiseLatch = new CountDownLatch(majority);
			this.acceptedLatch = new CountDownLatch(majority);
		}
	}

// extend / implement whatever interface, etc. as required.
// NO OTHER public members / methods allowed. broadcastTOMsg, acceptTOMsg, and shutdownPaxos must be the only visible methods to the application layer.
//You should also not change the signature of these methods (arguments and return value) other aspects maybe changed with reasonable design needs.
public class Paxos
{
	GCL gcl;
	FailCheck failCheck;
	Logger logger;
	String myProcess;
	String[] allValidProcesses;

	private int currInstance = 0;

	// NOTE: concurrentHashMaps are thread safe
	private final Map<Integer, Integer> highestPromisedBallot = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> highestAcceptedBallot = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> acceptedValues = new ConcurrentHashMap<>();

	private final Map<Integer, BallotContext> activeBallots = new ConcurrentHashMap<>();

	//blocking queue to ensure TO
	private final BlockingQueue<Object> deliveredQueue = new LinkedBlockingQueue<>();

	private Thread listenerThread;
	private volatile boolean running = true; // volatile booleans are

	int ballotID;

	public Paxos(String myProcess, String[] allGroupProcesses, Logger logger, FailCheck failCheck) throws IOException, UnknownHostException
	{
		// Remember to call the failCheck.checkFailure(...) with appropriate arguments throughout your Paxos code to force fail points if necessary.
		this.failCheck = failCheck;

		// Initialize the GCL communication system as well as anything else you need to.
		this.gcl = new GCL(myProcess, allGroupProcesses, null, logger) ;
		this.logger = logger;

		this.myProcess = myProcess;

		this.allValidProcesses = allGroupProcesses;

		logger.info("[PAXOS INIT] Initialized Paxos for process: " + myProcess);

		//start listening for msgs
		this.listenerThread = new Thread(this::listenLoop);
		this.listenerThread.start();
	}

	//loop to listen for incoming msgs
	private void listenLoop(){

		try{
			while (running){
				GCMessage gcMsg = gcl.readGCMessage();
				if (gcMsg == null || gcMsg.val == null)
				{
					break;
				}

				if (gcMsg.val instanceof PaxosMessage)
				{
					PaxosMessage pmsg = (PaxosMessage) gcMsg.val;
					handlePaxosMsg(pmsg);

				}

			}
		} catch (InterruptedException e) {
			logger.info("[ListenLoop] Listener thread interrupted for process: " + myProcess);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error in Paxos Listening Loop: ", e );
		}
	}

	// synchronised: can only be used by 1 thread at a time
	private synchronized void handlePaxosMsg (PaxosMessage msg) {

		//TODO: fix
		failCheck.checkFailure(myProcess, "handlePaxosMessage");

		switch (msg.type) {
			case "PROPOSE": {
				int inst = msg.instance;
				int balNum = msg.ballotNum;
				int currPromise = highestAcceptedBallot.getOrDefault(inst, -1); //get inst, or default to -1

				logger.info(String.format("[%s] Received PROPOSE for instance %d with ballot %d from %s", myProcess, inst, balNum, msg.sender));

				if (balNum > currPromise) {
					highestPromisedBallot.put(inst, balNum);
					failCheck.checkFailure(myProcess, "propose"); //TODO

					// promise highest value
					int accepted = highestAcceptedBallot.getOrDefault(inst, -1);
					Object accVal = acceptedValues.get(inst);

					logger.info(String.format("[%s] Promising ballot %d for instance %d. Sending PROMISE back to %s (highest accepted bal: %d)",
							myProcess, balNum, inst, msg.sender, accepted));

					//create new PROMISE reply msg
					PaxosMessage promise = new PaxosMessage("PROMISE", inst, balNum, myProcess, accVal, accepted);
					gcl.sendMsg(promise, msg.sender);

				} else {
					logger.info(String.format("[%s] REJECTED PROPOSE for instance %d with ballot %d (already promised ballot %d)",
							myProcess, inst, balNum, currPromise));
				}
			}
			break;
			case "PROMISE": {
				int inst = msg.instance;
				int balNum = msg.ballotNum;
				BallotContext ctx = activeBallots.get(inst);

				if (ctx != null && ctx.ballotNum == balNum) {
					if (ctx.promisedProcs.add(msg.sender)) {
						if (msg.acceptedBal > ctx.highestOtherAcceptedBallot) {
							ctx.highestOtherAcceptedBallot = msg.acceptedBal;
							ctx.highestOtherAcceptedValue = msg.value;
						}
						ctx.promiseLatch.countDown();
						logger.info(String.format("[%s] Received PROMISE for instance %d, ballot %d from %s. Total promises: %d",
								myProcess, inst, balNum, msg.sender, (allValidProcesses.length / 2 + 1) - ctx.promiseLatch.getCount()));
					}
				}
				break;
			}
			case "ACCEPT":
			{
				int inst = msg.instance;
				int balNum = msg.ballotNum;
				int currPromise = highestPromisedBallot.getOrDefault(inst, -1);

				logger.info(String.format("[%s] Received ACCEPT request for instance %d, ballot %d from %s with value: %s",
						myProcess, inst, balNum, msg.sender, msg.value));

				//  check against highest promised ballot
				if (balNum >= currPromise) {
					highestPromisedBallot.put(inst, balNum);
					highestAcceptedBallot.put(inst, balNum);
					acceptedValues.put(inst, msg.value);

					failCheck.checkFailure(myProcess, "accept_accepted");

					logger.info(String.format("[%s] Accepted value for instance %d, ballot %d. Sending ACCEPTED acknowledgment to %s",
							myProcess, inst, balNum, msg.sender));

					// Reply with ACCEPTED ack back to proposer
					PaxosMessage acceptedMsg = new PaxosMessage("ACCEPTED", inst, balNum, myProcess, msg.value, -1);
					gcl.sendMsg(acceptedMsg, msg.sender);
				} else {
					logger.info(String.format("[%s] REJECTED ACCEPT for instance %d, ballot %d (promised higher ballot %d)",
							myProcess, inst, balNum, currPromise));
				}
				break;
			}
			case "ACCEPTED":
			{
				int inst = msg.instance;
				int balNum = msg.ballotNum;
				BallotContext ctx = activeBallots.get(inst);

				if (ctx != null && ctx.ballotNum == balNum) {
					if (ctx.acceptedProcs.add(msg.sender)) {
						ctx.acceptedLatch.countDown();
						logger.info(String.format("[%s] Received ACCEPTED ack for instance %d, ballot %d from %s. Total accepts: %d",
								myProcess, inst, balNum, msg.sender, (allValidProcesses.length / 2 + 1) - ctx.acceptedLatch.getCount()));
					}
				}
				break;
		}
		default:
			logger.warning(String.format("[%s] Received unknown message type: %s", myProcess, msg.type));
			break;
		}
	}

	// paxos recieves msg (move) from app layer. wants to add move to total order.
	public void broadcastTOMsg(Object val)
	{
		// This is just a place holder.
		// Extend this to build whatever Paxos logic you need to make sure the messaging system is total order.
		// Here you will have to ensure that the CALL BLOCKS, and is returned ONLY when a majority (and immediately upon majority) of processes have accepted the value.
		failCheck.checkFailure(myProcess, "broadcastTOMsg_entry");

		int instanceToRun;
		synchronized (this) {
			instanceToRun = currInstance++;
		}
		logger.info(String.format("[%s] App requested broadcast for value: %s assigned to instance %d", myProcess, val, instanceToRun));

		Object chosenValue = runConsensus(instanceToRun, val);

		logger.info(String.format("[%s] Consensus successfully reached for instance %d with committed value: %s", myProcess, instanceToRun, chosenValue));

		// Push successfully agreed-upon message to local delivery queue
		try {
			deliveredQueue.put(chosenValue);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// keep running paxos until a majority is reached
	private Object runConsensus(int instance, Object proposedVal) {
		int localBallotSeq = 0;

		while (running) {
			failCheck.checkFailure(myProcess, "consensus_loop_start");

			// Generate a unique ballot number
			localBallotSeq++;
			int ballotNum = localBallotSeq * 100 + Math.abs(myProcess.hashCode() % 100);

			logger.info(String.format("[%s] Starting consensus round for instance %d with generated ballot %d", myProcess, instance, ballotNum));

			BallotContext ctx = new BallotContext(ballotNum, allValidProcesses.length);
			activeBallots.put(instance, ctx);

			// 1. Send PROPOSE
			PaxosMessage proposeMsg = new PaxosMessage("PROPOSE", instance, ballotNum, myProcess, null, -1);
			logger.info(String.format("[%s] Broadcasting PROPOSE for instance %d, ballot %d", myProcess, instance, ballotNum));
			gcl.broadcastMsg(proposeMsg);

			gcl.broadcastMsg(proposeMsg);

			// Wait for majority promises with a timeout (e.g., 2000ms) to handle retries/livelocks
			boolean receivedPromises = false;
			try {
				receivedPromises = ctx.promiseLatch.await(2000, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				if (!running) return proposedVal;
				Thread.currentThread().interrupt();
			}

			if (!receivedPromises) {
				logger.warning(String.format("[%s] Timeout waiting for PROMISE quorum for instance %d, ballot %d. Retrying...", myProcess, instance, ballotNum));
				activeBallots.remove(instance);
				continue; // Retry with a higher ballot number
			}

			failCheck.checkFailure(myProcess, "phase1_completed");
			logger.info(String.format("[%s] Quorum of PROMISES reached for instance %d, ballot %d", myProcess, instance, ballotNum));

			//2. Send ACCEPT
			// If any acceptor returned a previously accepted value, safety dictates we adopt the value with the highest ballot
			Object valueToAccept = proposedVal;
			if (ctx.highestOtherAcceptedValue != null) {
				valueToAccept = ctx.highestOtherAcceptedValue;
				logger.info(String.format("[%s] Adopting previously accepted value from another ballot: %s", myProcess, valueToAccept));
			}

			PaxosMessage acceptMsg = new PaxosMessage("ACCEPT", instance, ballotNum, myProcess, valueToAccept, -1);
			logger.info(String.format("[%s] Broadcasting ACCEPT for instance %d, ballot %d with value: %s", myProcess, instance, ballotNum, valueToAccept));

			gcl.broadcastMsg(acceptMsg);

			// Wait for majority ACCEPTED acknowledgments
			boolean receivedAccepts = false;
			try {
				receivedAccepts = ctx.acceptedLatch.await(2000, TimeUnit.MILLISECONDS);

			} catch (InterruptedException e) {
				if (!running) return proposedVal;
				Thread.currentThread().interrupt();
			}

			if (!receivedAccepts) {
				logger.warning(String.format("[%s] Timeout waiting for ACCEPTED quorum for instance %d, ballot %d. Retrying...", myProcess, instance, ballotNum));
				activeBallots.remove(instance);
				continue; // Retry with a higher ballot number
			}

			failCheck.checkFailure(myProcess, "consensus_reached");
			logger.info(String.format("[%s] Quorum of ACCEPTED acks reached for instance %d, ballot %d", myProcess, instance, ballotNum));
			activeBallots.remove(instance);
			return valueToAccept;
		}
		return proposedVal;
	}

	// This is what the application layer is calling to figure out what is the next message in the total order.
	// Messages delivered in ALL the processes in the group should deliver this in the same order.

	public Object acceptTOMsg() throws InterruptedException {
		failCheck.checkFailure(myProcess, "acceptTOMsg");
		return deliveredQueue.take();
	}

	public void shutdownPaxos() {
		logger.info(String.format("[%s] Shutting down Paxos protocol layer...", myProcess));
		running = false;
		if (listenerThread != null) {
			listenerThread.interrupt();
		}
		gcl.shutdownGCL();

		logger.info(String.format("[%s] Paxos layer successfully shutdown.", myProcess));
	}
}

